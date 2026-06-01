package io.umaboot.core.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * Loads and validates a {@code umaboot.yaml} file into a {@link UmabootConfig}.
 */
public final class UmabootConfigLoader {

    private UmabootConfigLoader() {}

    public static UmabootConfig load(Path yamlFile) {
        Objects.requireNonNull(yamlFile, "yamlFile");
        if (!Files.exists(yamlFile)) {
            throw new IllegalArgumentException("Config file not found: " + yamlFile);
        }
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        try (var reader = Files.newBufferedReader(yamlFile, StandardCharsets.UTF_8)) {
            Map<String, Object> root = yaml.load(reader);
            if (root == null) {
                throw new IllegalArgumentException("Empty config file: " + yamlFile);
            }
            return fromMap(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + yamlFile, e);
        }
    }

    @SuppressWarnings("unchecked")
    static UmabootConfig fromMap(Map<String, Object> root) {
        Map<String, Object> conn = mapOrEmpty(root, "connection");
        Map<String, Object> gen = mapOrEmpty(root, "generation");
        if (gen.isEmpty()) {
            throw new IllegalArgumentException("Missing 'generation' section");
        }
        // Either `connection:` (live JDBC) or `schemaFile:` (parsed .sql file) must be present.
        // The cross-field XOR check lives in UmabootConfig's compact constructor; here we just
        // tolerate either one being absent and let the model record reject the bad combinations.
        boolean hasSchemaFile = root.containsKey("schemaFile")
                && root.get("schemaFile") != null
                && !root.get("schemaFile").toString().isBlank();
        if (conn.isEmpty() && !hasSchemaFile) {
            throw new IllegalArgumentException(
                    "Missing schema source: set either 'connection' (live database) "
                            + "or 'schemaFile' (path to a .sql DDL file).");
        }

        var connection = conn.isEmpty() ? null : parseConnection(conn);

        Map<String, Object> jpaMap = mapOrEmpty(gen, "jpa");
        Map<String, Object> myBatisMap = mapOrEmpty(gen, "mybatis");
        Map<String, Object> tablesMap = mapOrEmpty(gen, "tables");
        Map<String, Object> dddMap = mapOrEmpty(gen, "ddd");
        Map<String, Object> outputMap = mapOrEmpty(gen, "output");

        var jpa = new UmabootConfig.JpaOptions(bool(jpaMap, "useMapStruct", false));
        var myBatis = new UmabootConfig.MyBatisOptions(str(myBatisMap, "style", "xml"));
        var tables = new UmabootConfig.TableFilterOptions(
                stringList(tablesMap, "include"),
                stringList(tablesMap, "exclude"),
                str(tablesMap, "classNameStripPrefix", ""),
                parseTableOverrides(mapOrEmpty(tablesMap, "overrides")));

        Map<String, Object> belongsToRaw = mapOrEmpty(dddMap, "belongsTo");
        java.util.Map<String, String> belongsTo = new java.util.LinkedHashMap<>();
        for (var entry : belongsToRaw.entrySet()) {
            if (entry.getValue() != null) belongsTo.put(entry.getKey(), entry.getValue().toString());
        }
        var ddd = new UmabootConfig.DddOptions(
                stringList(dddMap, "aggregateRoots"),
                stringList(dddMap, "nonRoots"),
                belongsTo,
                str(dddMap, "sharedKernelPackage", "shared"));

        var output = new UmabootConfig.OutputOptions(str(outputMap, "mode", "standalone"));

        // OpenAPI: prefer new openapi.style; fall back to legacy generateOpenApi boolean.
        Map<String, Object> openapiMap = mapOrEmpty(gen, "openapi");
        String openapiStyle = str(openapiMap, "style", null);
        if (openapiStyle == null) {
            // Legacy: generateOpenApi: true|false maps to yaml|none.
            Object legacy = gen.get("generateOpenApi");
            if (legacy != null) {
                boolean enabled = legacy instanceof Boolean b ? b : Boolean.parseBoolean(legacy.toString());
                openapiStyle = enabled ? "yaml" : "none";
            } else {
                openapiStyle = "yaml";
            }
        }
        var openapi = new UmabootConfig.OpenApiOptions(openapiStyle);

        // Injection style. Defaults to "constructor" (modern, no annotations).
        Map<String, Object> injectionMap = mapOrEmpty(gen, "injection");
        var injection = new UmabootConfig.InjectionOptions(str(injectionMap, "style", "constructor"));

        // Validation style. Defaults to "jakarta" (current behavior).
        Map<String, Object> validationMap = mapOrEmpty(gen, "validation");
        var validation = new UmabootConfig.ValidationOptions(str(validationMap, "style", "jakarta"));

        // DTO style + shape. Defaults to class + separate (current behavior).
        Map<String, Object> dtoMap = mapOrEmpty(gen, "dto");
        var dto = new UmabootConfig.DtoOptions(
                str(dtoMap, "style", "class"),
                str(dtoMap, "shape", "separate"));

        // Exception style. Defaults to "problemdetail" (current behavior).
        Map<String, Object> exceptionMap = mapOrEmpty(gen, "exception");
        var exception = new UmabootConfig.ExceptionOptions(str(exceptionMap, "style", "problemdetail"));

        // Audit fields auto-detection. Default enabled with conventional column names.
        Map<String, Object> auditMap = mapOrEmpty(gen, "audit");
        var audit = new UmabootConfig.AuditOptions(
                bool(auditMap, "enabled", true),
                str(auditMap, "createdAt", "created_at"),
                str(auditMap, "updatedAt", "updated_at"),
                str(auditMap, "createdBy", "created_by"),
                str(auditMap, "updatedBy", "updated_by"));

        // Soft delete auto-detection. Default enabled; null column = auto-detect.
        Map<String, Object> softDeleteMap = mapOrEmpty(gen, "softDelete");
        var softDelete = new UmabootConfig.SoftDeleteOptions(
                bool(softDeleteMap, "enabled", true),
                str(softDeleteMap, "column", null));

        // Docker scaffolding. Off by default.
        Map<String, Object> dockerMap = mapOrEmpty(gen, "docker");
        int dockerPort = 8080;
        Object portRaw = dockerMap.get("port");
        if (portRaw instanceof Number n) dockerPort = n.intValue();
        else if (portRaw != null) try { dockerPort = Integer.parseInt(portRaw.toString()); } catch (NumberFormatException ignored) {}
        var docker = new UmabootConfig.DockerOptions(
                bool(dockerMap, "enabled", false),
                str(dockerMap, "baseImage", "eclipse-temurin:17-jre-alpine"),
                dockerPort);

        // CI pipeline scaffolding. None by default.
        Map<String, Object> ciMap = mapOrEmpty(gen, "ci");
        var ci = new UmabootConfig.CiOptions(str(ciMap, "style", "none"));

        // Logging style. Plain by default.
        Map<String, Object> loggingMap = mapOrEmpty(gen, "logging");
        var logging = new UmabootConfig.LoggingOptions(
                str(loggingMap, "style", "plain"),
                bool(loggingMap, "correlationId", false));

        // Tests. Off by default.
        Map<String, Object> testsMap = mapOrEmpty(gen, "tests");
        var tests = new UmabootConfig.TestOptions(bool(testsMap, "enabled", false));

        // Pagination. Offset by default.
        Map<String, Object> paginationMap = mapOrEmpty(gen, "pagination");
        var pagination = new UmabootConfig.PaginationOptions(str(paginationMap, "style", "offset"));

        // Security. None by default.
        Map<String, Object> securityMap = mapOrEmpty(gen, "security");
        java.util.List<UmabootConfig.UserCredentials> securityUsers = new java.util.ArrayList<>();
        Object usersRaw = securityMap.get("users");
        if (usersRaw instanceof java.util.List<?> usersList) {
            for (Object u : usersList) {
                if (!(u instanceof Map<?, ?> entry)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> userMap = (Map<String, Object>) entry;
                String username = str(userMap, "username", null);
                String password = str(userMap, "password", null);
                if (username == null || password == null) continue;
                securityUsers.add(new UmabootConfig.UserCredentials(
                        username, password, stringList(userMap, "roles")));
            }
        }
        Map<String, Object> jwtMap = mapOrEmpty(securityMap, "jwt");
        int jwtExpiration = 60;
        Object expRaw = jwtMap.get("expirationMinutes");
        if (expRaw instanceof Number n) jwtExpiration = n.intValue();
        else if (expRaw != null) try { jwtExpiration = Integer.parseInt(expRaw.toString()); } catch (NumberFormatException ignored) {}
        var jwt = new UmabootConfig.JwtOptions(
                str(jwtMap, "secret", null),
                jwtExpiration,
                str(jwtMap, "header", "Authorization"),
                str(jwtMap, "prefix", "Bearer "));
        var security = new UmabootConfig.SecurityOptions(
                str(securityMap, "style", "none"),
                securityUsers,
                jwt);

        // Application config format. yaml by default.
        Map<String, Object> appConfigMap = mapOrEmpty(gen, "applicationConfig");
        var applicationConfig = new UmabootConfig.ApplicationConfigOptions(
                str(appConfigMap, "format", "yaml"));

        var generation = new UmabootConfig.Generation(
                str(gen, "architecture", "mvc"),
                str(gen, "persistence", "jpa"),
                requireString(gen, "basePackage"),
                requireString(gen, "projectName"),
                str(gen, "projectGroup", "com.example"),
                str(gen, "springBootVersion", "3.3.5"),
                str(gen, "javaVersion", "17"),
                bool(gen, "useLombok", true),
                openapi,
                injection,
                validation,
                dto,
                exception,
                audit,
                softDelete,
                docker,
                ci,
                logging,
                tests,
                pagination,
                security,
                str(gen, "outputDir", null),
                jpa,
                myBatis,
                tables,
                ddd,
                output,
                applicationConfig,
                str(root, "schemaFile", null),
                str(gen, "buildTool", "maven"));

        return new UmabootConfig(connection, generation);
    }

    /**
     * Parses the {@code connection:} YAML section into a {@link UmabootConfig.Connection}.
     *
     * <p>Accepts two shapes:</p>
     * <ul>
     *   <li><b>New shape</b> ({@code mode: host | url}): explicit fields. {@code host} mode
     *       requires {@code type}, {@code host}, {@code database}; {@code url} mode requires
     *       {@code url}. The {@code database} field falls back to a parse of the URL when
     *       absent in {@code url} mode.</li>
     *   <li><b>Legacy shape</b> (no {@code mode}): infers {@code mode = url}, derives
     *       {@code type} from the URL prefix, and parses {@code database} from the URL path.
     *       The legacy {@code schema} field is preserved as-is. This is what older
     *       {@code umaboot.yaml} files contain — they continue to load without manual edits.</li>
     * </ul>
     *
     * <p>The first save by the panel/CLI rewrites a legacy yaml in the new shape.</p>
     */
    /**
     * Parses {@code generation.tables.overrides:} into a map of
     * {@code tableName -> TableOverride}. Tolerant of partial entries:
     * a table block can have {@code className} only, {@code columns} only, or both.
     */
    @SuppressWarnings("unchecked")
    private static java.util.Map<String, UmabootConfig.TableOverride> parseTableOverrides(Map<String, Object> overridesMap) {
        if (overridesMap == null || overridesMap.isEmpty()) return java.util.Map.of();
        java.util.Map<String, UmabootConfig.TableOverride> result = new java.util.LinkedHashMap<>();
        for (var entry : overridesMap.entrySet()) {
            String tableName = entry.getKey();
            if (!(entry.getValue() instanceof Map<?, ?> rawTable)) continue;
            Map<String, Object> tableMap = (Map<String, Object>) rawTable;

            String className = str(tableMap, "className", "");

            java.util.Map<String, UmabootConfig.ColumnOverride> columns = new java.util.LinkedHashMap<>();
            Object rawColumns = tableMap.get("columns");
            if (rawColumns instanceof Map<?, ?> columnsRawMap) {
                for (var c : columnsRawMap.entrySet()) {
                    if (!(c.getValue() instanceof Map<?, ?> rawCol)) continue;
                    Map<String, Object> colMap = (Map<String, Object>) rawCol;
                    String javaType = str(colMap, "javaType", "");
                    if (!javaType.isEmpty()) {
                        columns.put(c.getKey().toString(), new UmabootConfig.ColumnOverride(javaType));
                    }
                }
            }

            UmabootConfig.TableOverride to = new UmabootConfig.TableOverride(className, columns);
            if (!to.isEmpty()) result.put(tableName, to);
        }
        return result;
    }

    private static UmabootConfig.Connection parseConnection(Map<String, Object> conn) {
        String mode = str(conn, "mode", null);
        // Legacy shape: no `mode` key -> treat as url mode, derive everything else from `url`.
        if (mode == null) {
            String legacyUrl = requireString(conn, "url");
            return new UmabootConfig.Connection(
                    "url",
                    /* type      */ null,        // canonical-form constructor will derive from URL
                    /* host      */ null,
                    /* params    */ null,
                    /* url       */ legacyUrl,
                    /* database  */ null,        // canonical-form constructor will parse from URL
                    /* schema    */ str(conn, "schema", "public"),
                    /* username  */ str(conn, "username", ""),
                    /* password  */ str(conn, "password", ""),
                    /* driver    */ str(conn, "driver", null));
        }
        // New shape.
        return new UmabootConfig.Connection(
                mode,
                str(conn, "type", null),
                str(conn, "host", null),
                str(conn, "params", null),
                str(conn, "url", null),
                str(conn, "database", null),
                str(conn, "schema", "public"),
                str(conn, "username", ""),
                str(conn, "password", ""),
                str(conn, "driver", null));
    }

    /**
     * Returns the value at {@code key} if it's a non-null Map; otherwise returns
     * an empty map. Treats both "key missing" and "key present with null/empty value"
     * the same way — which is what we want for optional config sections.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapOrEmpty(Map<String, Object> parent, String key) {
        Object v = parent.get(key);
        if (v instanceof Map<?, ?>) {
            return (Map<String, Object>) v;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private static java.util.List<String> stringList(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return java.util.List.of();
        if (v instanceof java.util.List<?> list) {
            java.util.List<String> result = new java.util.ArrayList<>(list.size());
            for (Object o : list) {
                if (o != null) result.add(o.toString());
            }
            return result;
        }
        // Tolerate a single string ("foo") as a one-element list
        return java.util.List.of(v.toString());
    }

    private static String requireString(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null || v.toString().isEmpty()) {
            throw new IllegalArgumentException("Missing required field: " + key);
        }
        return v.toString();
    }

    private static String str(Map<String, Object> m, String key, String defaultValue) {
        Object v = m.get(key);
        return v == null ? defaultValue : v.toString();
    }

    private static boolean bool(Map<String, Object> m, String key, boolean defaultValue) {
        Object v = m.get(key);
        if (v == null) return defaultValue;
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(v.toString());
    }
}

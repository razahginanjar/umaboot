package io.umaboot.core.config;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Read/write helper for {@code umaboot.yaml}.
 *
 * <p>Loading delegates to {@link UmabootConfigLoader}. Saving serializes a
 * {@link UmabootConfig} back to YAML. Round-tripping does <em>not</em> preserve
 * comments — if a user wants commented configs they should hand-edit the file
 * directly. The IntelliJ Settings panel uses this writer to persist UI changes.</p>
 */
public final class UmabootYamlIO {

    private UmabootYamlIO() {}

    /** Convenience: loads via {@link UmabootConfigLoader#load(Path)}. */
    public static UmabootConfig load(Path file) {
        return UmabootConfigLoader.load(file);
    }

    /** Writes the given config to {@code file}, creating parent dirs as needed. */
    public static void save(Path file, UmabootConfig config) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(config, "config");
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setIndent(2);
            options.setPrettyFlow(true);
            Yaml yaml = new Yaml(options);
            String content = yaml.dump(toMap(config));
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + file, e);
        }
    }

    /** Build the same nested-map shape that {@link UmabootConfigLoader} consumes. */
    public static Map<String, Object> toMap(UmabootConfig config) {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> conn = new LinkedHashMap<>();
        var c = config.connection();
        // Always-present fields
        conn.put("mode", c.mode());
        conn.put("type", c.type());
        // Mode-specific shape
        if ("host".equals(c.mode())) {
            conn.put("host", c.host());
            if (c.params() != null && !c.params().isEmpty()) {
                conn.put("params", c.params());
            }
        } else {
            conn.put("url", c.url());
        }
        conn.put("database", c.database());
        conn.put("schema", c.schema());
        conn.put("username", c.username());
        conn.put("password", c.password());
        root.put("connection", conn);

        Map<String, Object> gen = new LinkedHashMap<>();
        gen.put("architecture", config.generation().architecture());
        gen.put("persistence", config.generation().persistence());
        gen.put("basePackage", config.generation().basePackage());
        gen.put("projectName", config.generation().projectName());
        gen.put("projectGroup", config.generation().projectGroup());
        gen.put("springBootVersion", config.generation().springBootVersion());
        gen.put("javaVersion", config.generation().javaVersion());
        gen.put("useLombok", config.generation().useLombok());

        Map<String, Object> openapi = new LinkedHashMap<>();
        openapi.put("style", config.generation().openapi().style());
        gen.put("openapi", openapi);

        Map<String, Object> injection = new LinkedHashMap<>();
        injection.put("style", config.generation().injection().style());
        gen.put("injection", injection);

        Map<String, Object> validation = new LinkedHashMap<>();
        validation.put("style", config.generation().validation().style());
        gen.put("validation", validation);

        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("style", config.generation().dto().style());
        dto.put("shape", config.generation().dto().shape());
        gen.put("dto", dto);

        Map<String, Object> exception = new LinkedHashMap<>();
        exception.put("style", config.generation().exception().style());
        gen.put("exception", exception);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("enabled", config.generation().audit().enabled());
        audit.put("createdAt", config.generation().audit().createdAt());
        audit.put("updatedAt", config.generation().audit().updatedAt());
        audit.put("createdBy", config.generation().audit().createdBy());
        audit.put("updatedBy", config.generation().audit().updatedBy());
        gen.put("audit", audit);

        Map<String, Object> softDelete = new LinkedHashMap<>();
        softDelete.put("enabled", config.generation().softDelete().enabled());
        if (config.generation().softDelete().column() != null) {
            softDelete.put("column", config.generation().softDelete().column());
        }
        gen.put("softDelete", softDelete);

        Map<String, Object> docker = new LinkedHashMap<>();
        docker.put("enabled", config.generation().docker().enabled());
        docker.put("baseImage", config.generation().docker().baseImage());
        docker.put("port", config.generation().docker().port());
        gen.put("docker", docker);

        Map<String, Object> ci = new LinkedHashMap<>();
        ci.put("style", config.generation().ci().style());
        gen.put("ci", ci);

        Map<String, Object> logging = new LinkedHashMap<>();
        logging.put("style", config.generation().logging().style());
        logging.put("correlationId", config.generation().logging().correlationId());
        gen.put("logging", logging);

        Map<String, Object> tests = new LinkedHashMap<>();
        tests.put("enabled", config.generation().tests().enabled());
        gen.put("tests", tests);

        Map<String, Object> pagination = new LinkedHashMap<>();
        pagination.put("style", config.generation().pagination().style());
        gen.put("pagination", pagination);

        Map<String, Object> security = new LinkedHashMap<>();
        security.put("style", config.generation().security().style());
        java.util.List<Map<String, Object>> users = new java.util.ArrayList<>();
        for (var u : config.generation().security().users()) {
            Map<String, Object> userMap = new LinkedHashMap<>();
            userMap.put("username", u.username());
            userMap.put("password", u.password());
            userMap.put("roles", u.roles());
            users.add(userMap);
        }
        security.put("users", users);
        Map<String, Object> jwtMap = new LinkedHashMap<>();
        if (config.generation().security().jwt().secret() != null) {
            jwtMap.put("secret", config.generation().security().jwt().secret());
        }
        jwtMap.put("expirationMinutes", config.generation().security().jwt().expirationMinutes());
        jwtMap.put("header", config.generation().security().jwt().header());
        jwtMap.put("prefix", config.generation().security().jwt().prefix());
        security.put("jwt", jwtMap);
        gen.put("security", security);

        gen.put("outputDir", config.generation().outputDir());

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("mode", config.generation().output().mode());
        gen.put("output", output);

        Map<String, Object> jpa = new LinkedHashMap<>();
        jpa.put("useMapStruct", config.generation().jpa().useMapStruct());
        gen.put("jpa", jpa);

        Map<String, Object> mybatis = new LinkedHashMap<>();
        mybatis.put("style", config.generation().mybatis().style());
        gen.put("mybatis", mybatis);

        Map<String, Object> tables = new LinkedHashMap<>();
        tables.put("include", config.generation().tables().include());
        tables.put("exclude", config.generation().tables().exclude());
        gen.put("tables", tables);

        Map<String, Object> ddd = new LinkedHashMap<>();
        ddd.put("aggregateRoots", config.generation().ddd().aggregateRoots());
        ddd.put("nonRoots", config.generation().ddd().nonRoots());
        ddd.put("belongsTo", config.generation().ddd().belongsTo());
        ddd.put("sharedKernelPackage", config.generation().ddd().sharedKernelPackage());
        gen.put("ddd", ddd);

        root.put("generation", gen);
        return root;
    }
}

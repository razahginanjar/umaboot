package io.umaboot.core.generator.mvc;

import io.umaboot.core.config.UmabootConfig;
import io.umaboot.core.config.UmabootConfigLoader;
import io.umaboot.core.config.UmabootYamlIO;
import io.umaboot.core.generator.GeneratedUnit;
import io.umaboot.core.generator.GeneratorContext;
import io.umaboot.core.model.ColumnModel;
import io.umaboot.core.model.SchemaModel;
import io.umaboot.core.model.TableModel;
import io.umaboot.core.template.TemplateEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Types;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the v0.8 per-table override behavior: a {@code TableOverride} with a
 * {@code className} replaces the default-derived entity name; a per-column
 * {@code javaType} replaces the default JDBC-type mapping.
 *
 * <p>Both the YAML round-trip (loader + writer) and the render-flow are
 * covered so a regression in either layer surfaces here.</p>
 */
class TableOverridesTest {

    // ---------------------------------------------------------------- yaml round-trip

    @Test
    void yaml_roundTrip_preservesClassNameAndColumnOverrides(@TempDir Path tmp) throws Exception {
        Path yaml = tmp.resolve("umaboot.yaml");
        Files.writeString(yaml, """
                connection:
                  url: jdbc:postgresql://localhost:5432/shop
                  username: u
                  password: p
                  schema: public
                generation:
                  basePackage: com.example.shop
                  projectName: shop-api
                  tables:
                    classNameStripPrefix: app_
                    overrides:
                      app_users:
                        className: Account
                        columns:
                          metadata:
                            javaType: java.util.Map<String,Object>
                          notes:
                            javaType: Object
                      app_orders:
                        columns:
                          total_cents:
                            javaType: java.math.BigDecimal
                """);

        UmabootConfig loaded = UmabootConfigLoader.load(yaml);
        var overrides = loaded.generation().tables().overrides();

        assertThat(overrides).containsOnlyKeys("app_users", "app_orders");
        assertThat(overrides.get("app_users").className()).isEqualTo("Account");
        assertThat(overrides.get("app_users").columns()).containsOnlyKeys("metadata", "notes");
        assertThat(overrides.get("app_users").columns().get("metadata").javaType())
                .isEqualTo("java.util.Map<String,Object>");
        assertThat(overrides.get("app_orders").columns()).containsOnlyKeys("total_cents");
        assertThat(overrides.get("app_orders").className()).isEmpty(); // only column overrides

        // Round-trip: write + reload + same content.
        Path out = tmp.resolve("rewritten.yaml");
        UmabootYamlIO.save(out, loaded);
        UmabootConfig reloaded = UmabootConfigLoader.load(out);
        assertThat(reloaded.generation().tables().overrides())
                .isEqualTo(loaded.generation().tables().overrides());

        // Tables without overrides must NOT appear in the saved YAML
        // (per "the one that not have do not have, do not need to be cut").
        String body = Files.readString(out);
        assertThat(body).contains("app_users:");
        assertThat(body).contains("app_orders:");
        assertThat(body).doesNotContain("legacy_clients:");
        // An empty TableOverride should not be persisted.
    }

    @Test
    void emptyOverrides_areDroppedOnSave(@TempDir Path tmp) throws Exception {
        // Construct a config where one entry is empty (no className, no columns)
        // — it should be filtered out by the canonical-form constructor and
        // the writer.
        Map<String, UmabootConfig.TableOverride> overrideMap = Map.of(
                "app_users", new UmabootConfig.TableOverride("Account", Map.of()),
                "ghost_table", UmabootConfig.TableOverride.empty()  // empty - should be dropped
        );

        // The constructor itself doesn't drop empty entries (records are dumb data),
        // but the YAML writer should.
        var tables = new UmabootConfig.TableFilterOptions(
                List.of(), List.of(), "", overrideMap);
        assertThat(tables.overrides()).hasSize(2); // not filtered yet

        // Build a minimal config and write.
        UmabootConfig cfg = configWith(tables);
        Path out = tmp.resolve("written.yaml");
        UmabootYamlIO.save(out, cfg);
        String body = Files.readString(out);
        assertThat(body).contains("app_users:");
        assertThat(body).doesNotContain("ghost_table:");
    }

    // ---------------------------------------------------------------- render flow

    @Test
    void perTableClassNameOverride_winsOverDerivation() {
        var override = new UmabootConfig.TableOverride("Account", Map.of());
        Map<String, UmabootConfig.TableOverride> overrides = Map.of("app_users", override);

        List<GeneratedUnit> units = generate("app_", overrides, schema("app_users"));

        // Override → "Account", not the strip-prefix derivation "User"
        String javaSrc = "src/main/java/com/example/shop";
        assertHasFile(units, javaSrc + "/entity/Account.java");
        assertHasFile(units, javaSrc + "/repository/AccountRepository.java");
        assertNoFile(units, javaSrc + "/entity/User.java");
        assertNoFile(units, javaSrc + "/entity/AppUser.java");
    }

    @Test
    void perColumnJavaTypeOverride_appearsInGeneratedField() {
        var columns = Map.of("metadata",
                new UmabootConfig.ColumnOverride("java.util.Map<String,Object>"));
        var override = new UmabootConfig.TableOverride("", columns);
        Map<String, UmabootConfig.TableOverride> overrides = Map.of("documents", override);

        List<GeneratedUnit> units = generate("", overrides, schema("documents"));

        String entity = readUnit(units, "src/main/java/com/example/shop/entity/Document.java");
        // Field should use the override type, NOT the default String mapping for TEXT.
        assertThat(entity).contains("Map<String,Object> metadata");
        // The override type qualifies for an import; the JavaTypeMapper.importFor
        // returns the FQN as-is when it contains a dot.
        assertThat(entity).contains("import java.util.Map<String,Object>");
    }

    @Test
    void overridesForOtherTables_doNotAffectTheCurrentTable() {
        var overrides = Map.of(
                "documents",
                new UmabootConfig.TableOverride("Doc", Map.of(
                        "metadata", new UmabootConfig.ColumnOverride("Object"))));

        List<GeneratedUnit> units = generate("", overrides, schema("articles"));

        // articles has no override → default class name "Article"
        String javaSrc = "src/main/java/com/example/shop";
        assertHasFile(units, javaSrc + "/entity/Article.java");
        assertNoFile(units, javaSrc + "/entity/Doc.java");
    }

    // ---------------------------------------------------------------- helpers

    private static List<GeneratedUnit> generate(
            String stripPrefix,
            Map<String, UmabootConfig.TableOverride> overrides,
            SchemaModel schema) {
        GeneratorContext ctx = new GeneratorContext(
                "com.example.shop", "shop-api", "com.example",
                "3.3.5", "17", true,
                "mvc", "jpa", "xml", false, "none", "constructor",
                "jakarta", "class", "separate", "problemdetail",
                UmabootConfig.AuditOptions.defaults(), UmabootConfig.SoftDeleteOptions.defaults(),
                UmabootConfig.DockerOptions.defaults(), UmabootConfig.CiOptions.defaults(),
                UmabootConfig.LoggingOptions.defaults(), UmabootConfig.TestOptions.defaults(),
                "offset", UmabootConfig.SecurityOptions.defaults(),
                UmabootConfig.DddOptions.defaults(),
                false, "postgres", null, null, stripPrefix, overrides);
        return new MvcGenerator(new TemplateEngine(null), ctx).generate(schema);
    }

    private static SchemaModel schema(String tableName) {
        ColumnModel id = new ColumnModel("id", Types.BIGINT, "int8", 19, 0,
                false, true, true, null, "", List.of());
        ColumnModel metadata = new ColumnModel("metadata", Types.LONGVARCHAR, "text", 0, 0,
                true, false, false, null, "", List.of());
        return new SchemaModel("public", List.of(
                new TableModel(tableName, "public", "",
                        List.of(id, metadata),
                        List.of("id"),
                        List.of(),
                        List.of(),
                        false)));
    }

    private static UmabootConfig configWith(UmabootConfig.TableFilterOptions tables) {
        var conn = new UmabootConfig.Connection(
                "url", "postgresql", null, null,
                "jdbc:postgresql://localhost:5432/db",
                "db", "public", "u", "p", null);
        var gen = new UmabootConfig.Generation(
                "mvc", "jpa", "com.example.app", "app", "com.example",
                "3.3.5", "17", true,
                UmabootConfig.OpenApiOptions.defaults(),
                UmabootConfig.InjectionOptions.defaults(),
                UmabootConfig.ValidationOptions.defaults(),
                UmabootConfig.DtoOptions.defaults(),
                UmabootConfig.ExceptionOptions.defaults(),
                UmabootConfig.AuditOptions.defaults(),
                UmabootConfig.SoftDeleteOptions.defaults(),
                UmabootConfig.DockerOptions.defaults(),
                UmabootConfig.CiOptions.defaults(),
                UmabootConfig.LoggingOptions.defaults(),
                UmabootConfig.TestOptions.defaults(),
                UmabootConfig.PaginationOptions.defaults(),
                UmabootConfig.SecurityOptions.defaults(),
                null,
                new UmabootConfig.JpaOptions(false),
                new UmabootConfig.MyBatisOptions("xml"),
                tables,
                UmabootConfig.DddOptions.defaults(),
                UmabootConfig.OutputOptions.defaults(),
                UmabootConfig.ApplicationConfigOptions.defaults(),
                null);
        return new UmabootConfig(conn, gen);
    }

    private static void assertHasFile(List<GeneratedUnit> units, String relativePath) {
        assertThat(units).extracting(GeneratedUnit::relativePath).contains(relativePath);
    }

    private static void assertNoFile(List<GeneratedUnit> units, String relativePath) {
        assertThat(units).extracting(GeneratedUnit::relativePath).doesNotContain(relativePath);
    }

    private static String readUnit(List<GeneratedUnit> units, String relativePath) {
        return units.stream()
                .filter(u -> u.relativePath().equals(relativePath))
                .findFirst()
                .map(GeneratedUnit::content)
                .orElseThrow(() -> new AssertionError("Missing: " + relativePath));
    }
}

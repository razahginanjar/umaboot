package io.umaboot.core.generator.mvc;

import io.umaboot.core.config.UmabootConfig;
import io.umaboot.core.generator.GeneratedUnit;
import io.umaboot.core.generator.GeneratorContext;
import io.umaboot.core.model.ColumnModel;
import io.umaboot.core.model.SchemaModel;
import io.umaboot.core.model.TableModel;
import io.umaboot.core.template.TemplateEngine;
import org.junit.jupiter.api.Test;

import java.sql.Types;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test for the {@code generation.tables.classNameStripPrefix} option:
 * a context configured with {@code stripPrefix=app_} and a table named
 * {@code app_users} must produce {@code User.java}, not {@code AppUser.java}.
 *
 * <p>Crucially, tables that <em>don't</em> start with the prefix are left
 * alone — so a single project-wide setting is safe even when a few tables
 * fall outside the convention.</p>
 */
class ClassNameStripPrefixRenderTest {

    @Test
    void prefix_strippedFromGeneratedFilePaths() {
        List<GeneratedUnit> units = generate(
                "app_",
                schemaWithTables("app_users", "app_orders"));

        String javaSrc = "src/main/java/com/example/shop";
        // Stripped → User / Order
        assertHasFile(units, javaSrc + "/entity/User.java");
        assertHasFile(units, javaSrc + "/repository/UserRepository.java");
        assertHasFile(units, javaSrc + "/controller/UserController.java");
        assertHasFile(units, javaSrc + "/entity/Order.java");
        // Un-stripped names must NOT be emitted
        assertNoFile(units, javaSrc + "/entity/AppUser.java");
        assertNoFile(units, javaSrc + "/entity/AppOrder.java");
    }

    @Test
    void prefix_strippedFromInsideEntityClassDeclaration() {
        List<GeneratedUnit> units = generate("app_", schemaWithTables("app_users"));
        String entity = readUnit(units, "src/main/java/com/example/shop/entity/User.java");
        assertThat(entity).contains("public class User");
        assertThat(entity).doesNotContain("class AppUser");
        // The table annotation still uses the actual SQL identifier
        assertThat(entity).contains("@Table(name = \"app_users\"");
    }

    @Test
    void tablesNotMatchingPrefix_areLeftUnstripped() {
        List<GeneratedUnit> units = generate(
                "app_",
                schemaWithTables("app_users", "legacy_clients", "flyway_schema_history"));

        String javaSrc = "src/main/java/com/example/shop";
        assertHasFile(units, javaSrc + "/entity/User.java");                  // matched, stripped
        assertHasFile(units, javaSrc + "/entity/LegacyClient.java");          // not matched, untouched
        assertHasFile(units, javaSrc + "/entity/FlywaySchemaHistory.java");   // not matched, untouched
    }

    @Test
    void emptyPrefix_equivalentToNoPrefix() {
        List<GeneratedUnit> units = generate("", schemaWithTables("app_users"));
        assertHasFile(units, "src/main/java/com/example/shop/entity/AppUser.java");
    }

    // ---- helpers ----

    private static List<GeneratedUnit> generate(String stripPrefix, SchemaModel schema) {
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
                false, "postgres", null, null, stripPrefix);
        return new MvcGenerator(new TemplateEngine(null), ctx).generate(schema);
    }

    private static SchemaModel schemaWithTables(String... names) {
        List<TableModel> tables = new java.util.ArrayList<>();
        for (String name : names) {
            ColumnModel id = new ColumnModel("id", Types.BIGINT, "int8", 19, 0,
                    false, true, true, null, "", List.of());
            ColumnModel email = new ColumnModel("email", Types.VARCHAR, "varchar", 255, 0,
                    false, false, false, null, "", List.of());
            tables.add(new TableModel(name, "public", "",
                    List.of(id, email),
                    List.of("id"),
                    List.of(),
                    List.of(),
                    false));
        }
        return new SchemaModel("public", tables);
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

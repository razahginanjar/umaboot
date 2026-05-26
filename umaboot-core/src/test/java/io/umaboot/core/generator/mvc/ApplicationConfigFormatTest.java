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
 * Locks the v0.8 application-config-format choice: when
 * {@code generation.applicationConfig.format} is {@code yaml} the project
 * ships {@code application.yml}; when {@code properties} it ships
 * {@code application.properties} in dotted-key form. Same datasource block
 * either way, just a different file format.
 */
class ApplicationConfigFormatTest {

    @Test
    void yamlFormat_emitsApplicationYmlOnly() {
        List<GeneratedUnit> units = generate(UmabootConfig.ApplicationConfigOptions.defaults());

        assertHasFile(units, "src/main/resources/application.yml");
        assertNoFile(units, "src/main/resources/application.properties");

        String yml = readUnit(units, "src/main/resources/application.yml");
        assertThat(yml).contains("spring:");
        assertThat(yml).contains("datasource:");
        assertThat(yml).contains("driver-class-name:");
    }

    @Test
    void propertiesFormat_emitsApplicationPropertiesOnly() {
        List<GeneratedUnit> units = generate(new UmabootConfig.ApplicationConfigOptions("properties"));

        assertHasFile(units, "src/main/resources/application.properties");
        assertNoFile(units, "src/main/resources/application.yml");

        String props = readUnit(units, "src/main/resources/application.properties");
        // Same content as the yml version but in dotted-key syntax
        assertThat(props).contains("spring.application.name=");
        assertThat(props).contains("spring.datasource.url=");
        assertThat(props).contains("spring.datasource.driver-class-name=");
        assertThat(props).contains("server.port=8080");
        // Must NOT contain yaml indented mapping syntax
        assertThat(props).doesNotContain("spring:");
        assertThat(props).doesNotContain("  datasource:");
    }

    @Test
    void propertiesFormat_preservesEnvVarFallbackForDatasource() {
        List<GeneratedUnit> units = generate(new UmabootConfig.ApplicationConfigOptions("properties"));

        String props = readUnit(units, "src/main/resources/application.properties");
        // The same ${SPRING_DATASOURCE_*:default} env-var fallback wraps the values
        assertThat(props).contains("spring.datasource.url=${SPRING_DATASOURCE_URL:");
        assertThat(props).contains("spring.datasource.username=${SPRING_DATASOURCE_USERNAME:");
        assertThat(props).contains("spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:");
    }

    @Test
    void invalidFormat_throwsAtConstruction() {
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> new UmabootConfig.ApplicationConfigOptions("toml"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("yaml");
    }

    // ---- helpers ----

    private static List<GeneratedUnit> generate(UmabootConfig.ApplicationConfigOptions appConfig) {
        SchemaModel schema = singleTableSchema();
        GeneratorContext ctx = new GeneratorContext(
                "com.example.shop", "shop-api", "com.example",
                "3.3.5", "17", true,
                "mvc", "jpa", "xml", false, "none", "constructor",
                "jakarta", "class", "separate", "problemdetail",
                UmabootConfig.AuditOptions.defaults(), UmabootConfig.SoftDeleteOptions.defaults(),
                UmabootConfig.DockerOptions.defaults(), UmabootConfig.CiOptions.defaults(), UmabootConfig.LoggingOptions.defaults(),
                UmabootConfig.TestOptions.defaults(), "offset", UmabootConfig.SecurityOptions.defaults(),
                UmabootConfig.DddOptions.defaults(), false, "postgres", null, appConfig, "");
        return new MvcGenerator(new TemplateEngine(null), ctx).generate(schema);
    }

    private static SchemaModel singleTableSchema() {
        ColumnModel id = new ColumnModel("id", Types.BIGINT, "int8", 19, 0,
                false, true, true, null, "", List.of());
        ColumnModel email = new ColumnModel("email", Types.VARCHAR, "varchar", 255, 0,
                false, false, false, null, "", List.of());
        return new SchemaModel("public", List.of(
                new TableModel("customers", "public", "",
                        List.of(id, email),
                        List.of("id"),
                        List.of(),
                        List.of(),
                        false)));
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

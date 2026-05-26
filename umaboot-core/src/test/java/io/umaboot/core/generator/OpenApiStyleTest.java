package io.umaboot.core.generator;

import io.umaboot.core.config.UmabootConfig;
import io.umaboot.core.generator.mvc.MvcGenerator;
import io.umaboot.core.model.ColumnModel;
import io.umaboot.core.model.SchemaModel;
import io.umaboot.core.model.TableModel;
import io.umaboot.core.template.TemplateEngine;
import org.junit.jupiter.api.Test;

import java.sql.Types;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@code openapi.style} switches the generator's output between
 * {@code openapi.yaml}, springdoc {@code OpenApiConfig.java}, and nothing.
 */
class OpenApiStyleTest {

    @Test
    void style_yaml_emitsStaticOpenapiYaml_withoutSpringdocAnnotations() {
        List<GeneratedUnit> units = generate("yaml");

        assertHasFile(units, "src/main/resources/openapi.yaml");
        assertNoFile(units, "src/main/java/com/example/shop/config/OpenApiConfig.java");

        // Controller has no springdoc annotations
        String controller = readUnit(units, "src/main/java/com/example/shop/controller/CustomerController.java");
        assertThat(controller).doesNotContain("@Tag")
                .doesNotContain("@Operation")
                .doesNotContain("io.swagger.v3.oas.annotations");

        // pom does NOT pull springdoc
        String pom = readUnit(units, "pom.xml");
        assertThat(pom).doesNotContain("springdoc-openapi-starter-webmvc-ui");
    }

    @Test
    void style_annotation_emitsConfigClass_andControllersHaveTagOperation() {
        List<GeneratedUnit> units = generate("annotation");

        assertNoFile(units, "src/main/resources/openapi.yaml");
        assertHasFile(units, "src/main/java/com/example/shop/config/OpenApiConfig.java");

        String openApiConfig = readUnit(units, "src/main/java/com/example/shop/config/OpenApiConfig.java");
        assertThat(openApiConfig)
                .contains("@OpenAPIDefinition")
                .contains("@Info")
                .contains("@Server")
                .contains("title = \"shop-api API\"");

        String controller = readUnit(units, "src/main/java/com/example/shop/controller/CustomerController.java");
        assertThat(controller)
                .contains("import io.swagger.v3.oas.annotations.Operation;")
                .contains("import io.swagger.v3.oas.annotations.tags.Tag;")
                .contains("@Tag(name = \"Customer\"")
                .contains("@Operation(summary = \"Create a new Customer\")")
                .contains("@Operation(summary = \"Get Customer by id\")")
                .contains("@Operation(summary = \"List Customers with pagination\")");

        // pom now pulls springdoc
        String pom = readUnit(units, "pom.xml");
        assertThat(pom)
                .contains("<artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>")
                .contains("<version>2.6.0</version>");
    }

    @Test
    void style_none_emitsNeitherFileNorAnnotations() {
        List<GeneratedUnit> units = generate("none");

        assertNoFile(units, "src/main/resources/openapi.yaml");
        assertNoFile(units, "src/main/java/com/example/shop/config/OpenApiConfig.java");

        String controller = readUnit(units, "src/main/java/com/example/shop/controller/CustomerController.java");
        assertThat(controller).doesNotContain("@Tag")
                .doesNotContain("@Operation");

        String pom = readUnit(units, "pom.xml");
        assertThat(pom).doesNotContain("springdoc");
    }

    // ---- helpers ----

    private static List<GeneratedUnit> generate(String openApiStyle) {
        SchemaModel schema = singleTableSchema();
        GeneratorContext ctx = new GeneratorContext(
                "com.example.shop", "shop-api", "com.example",
                "3.3.5", "17", true,
                "mvc", "jpa", "xml", false, openApiStyle, "constructor",
                "jakarta", "class", "separate", "problemdetail",
                UmabootConfig.AuditOptions.defaults(), UmabootConfig.SoftDeleteOptions.defaults(),
                UmabootConfig.DockerOptions.defaults(), UmabootConfig.CiOptions.defaults(), UmabootConfig.LoggingOptions.defaults(),
                UmabootConfig.TestOptions.defaults(), "offset", UmabootConfig.SecurityOptions.defaults(),
                UmabootConfig.DddOptions.defaults(),
                false, "postgres", null, null);
        return new MvcGenerator(new TemplateEngine(null), ctx).generate(schema);
    }

    private static SchemaModel singleTableSchema() {
        ColumnModel id = new ColumnModel("id", Types.BIGINT, "int8", 19, 0,
                false, true, true, null, "", List.of());
        ColumnModel email = new ColumnModel("email", Types.VARCHAR, "varchar", 255, 0,
                false, false, false, null, "", List.of());
        TableModel customers = new TableModel("customers", "public", "",
                List.of(id, email), List.of("id"),
                List.of(), List.of(), false);
        return new SchemaModel("public", List.of(customers));
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

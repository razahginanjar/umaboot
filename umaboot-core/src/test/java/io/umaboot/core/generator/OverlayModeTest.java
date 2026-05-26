package io.umaboot.core.generator;

import io.umaboot.core.config.UmabootConfig;
import io.umaboot.core.generator.ddd.DddGenerator;
import io.umaboot.core.generator.hexagonal.HexagonalGenerator;
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
 * Verifies overlay mode skips project-wide files across all architectures
 * while still emitting the per-table source files.
 */
class OverlayModeTest {

    @Test
    void mvcOverlay_skipsProjectWideFilesButEmitsSources() {
        List<GeneratedUnit> units = generate("mvc", true);

        // Project-wide files are NOT emitted
        assertNoFile(units, "pom.xml");
        assertNoFile(units, "src/main/resources/application.yml");
        assertNoFile(units, "src/main/java/com/example/shop/Application.java");
        assertNoFile(units, "src/main/java/com/example/shop/exception/GlobalExceptionHandler.java");

        // Per-table source files ARE emitted
        String javaSrc = "src/main/java/com/example/shop";
        assertHasFile(units, javaSrc + "/entity/Customer.java");
        assertHasFile(units, javaSrc + "/repository/CustomerRepository.java");
        assertHasFile(units, javaSrc + "/controller/CustomerController.java");
        assertHasFile(units, javaSrc + "/service/CustomerService.java");
        assertHasFile(units, javaSrc + "/service/impl/CustomerServiceImpl.java");
    }

    @Test
    void hexagonalOverlay_skipsProjectWideFilesButEmitsSources() {
        List<GeneratedUnit> units = generate("hexagonal", true);

        assertNoFile(units, "pom.xml");
        assertNoFile(units, "src/main/resources/application.yml");
        assertNoFile(units, "src/main/java/com/example/shop/Application.java");
        assertNoFile(units, "src/main/java/com/example/shop/adapter/in/web/GlobalExceptionHandler.java");

        String javaSrc = "src/main/java/com/example/shop";
        assertHasFile(units, javaSrc + "/domain/model/Customer.java");
        assertHasFile(units, javaSrc + "/domain/port/CustomerRepository.java");
        assertHasFile(units, javaSrc + "/adapter/in/web/CustomerController.java");
        assertHasFile(units, javaSrc + "/adapter/out/persistence/CustomerJpaEntity.java");
    }

    @Test
    void dddOverlay_skipsProjectWideFilesButEmitsAggregates() {
        List<GeneratedUnit> units = generate("ddd", true);

        assertNoFile(units, "pom.xml");
        assertNoFile(units, "src/main/resources/application.yml");
        assertNoFile(units, "src/main/java/com/example/shop/Application.java");
        assertNoFile(units, "src/main/java/com/example/shop/interfaces/rest/GlobalExceptionHandler.java");
        // DomainEvent is also skipped in overlay (user may already have one)
        assertNoFile(units, "src/main/java/com/example/shop/domain/shared/DomainEvent.java");

        String javaSrc = "src/main/java/com/example/shop";
        assertHasFile(units, javaSrc + "/domain/customer/Customer.java");
        assertHasFile(units, javaSrc + "/application/customer/CustomerApplicationService.java");
        assertHasFile(units, javaSrc + "/interfaces/rest/CustomerController.java");
        assertHasFile(units, javaSrc + "/infrastructure/persistence/CustomerJpaEntity.java");
    }

    @Test
    void standaloneMode_emitsAllFiles_unchangedFromV0_5() {
        List<GeneratedUnit> units = generate("mvc", false);

        // Standalone keeps everything
        assertHasFile(units, "pom.xml");
        assertHasFile(units, "src/main/resources/application.yml");
        assertHasFile(units, "src/main/java/com/example/shop/Application.java");
        assertHasFile(units, "src/main/java/com/example/shop/exception/GlobalExceptionHandler.java");
    }

    // ---- helpers ----

    private static List<GeneratedUnit> generate(String architecture, boolean overlay) {
        SchemaModel schema = singleTableSchema();
        GeneratorContext ctx = new GeneratorContext(
                "com.example.shop", "shop-api", "com.example",
                "3.3.5", "17", true,
                architecture, "jpa", "xml", false, "none", "constructor",
                "jakarta", "class", "separate", "problemdetail",
                UmabootConfig.AuditOptions.defaults(), UmabootConfig.SoftDeleteOptions.defaults(),
                UmabootConfig.DockerOptions.defaults(), UmabootConfig.CiOptions.defaults(), UmabootConfig.LoggingOptions.defaults(),
                UmabootConfig.TestOptions.defaults(), "offset", UmabootConfig.SecurityOptions.defaults(),
                UmabootConfig.DddOptions.defaults(),
                overlay, "postgres", null, null);
        TemplateEngine engine = new TemplateEngine(null);
        return switch (architecture) {
            case "mvc" -> new MvcGenerator(engine, ctx).generate(schema);
            case "hexagonal" -> new HexagonalGenerator(engine, ctx).generate(schema);
            case "ddd" -> new DddGenerator(engine, ctx).generate(schema);
            default -> throw new IllegalArgumentException(architecture);
        };
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
}

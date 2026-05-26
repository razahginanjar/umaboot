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
 * Verifies Phase B contract: every architecture's Controller wraps responses
 * with {@code ResponseEntity<...>}, returns a {@code PageResponse<T>} on
 * findAll, and all generators emit a {@code common/PageResponse.java} helper.
 */
class ResponseEntityAndPageResponseTest {

    @Test
    void mvcController_usesResponseEntityAndPageResponse() {
        List<GeneratedUnit> units = generateMvc();

        assertHasFile(units, "src/main/java/com/example/shop/common/PageResponse.java");

        String controller = readUnit(units, "src/main/java/com/example/shop/controller/CustomerController.java");
        assertThat(controller).contains("ResponseEntity<CustomerResponseDTO> create(");
        assertThat(controller).contains("ResponseEntity.status(HttpStatus.CREATED).body(");
        assertThat(controller).contains("ResponseEntity<CustomerResponseDTO> findById(");
        assertThat(controller).contains("ResponseEntity.ok(service.findById(id))");
        assertThat(controller).contains("ResponseEntity<PageResponse<CustomerResponseDTO>> findAll(");
        assertThat(controller).contains("PageResponse.of(page)");
        assertThat(controller).contains("ResponseEntity.noContent().build()");

        String pageResponse = readUnit(units, "src/main/java/com/example/shop/common/PageResponse.java");
        assertThat(pageResponse)
                .contains("public static <T> PageResponse<T> of(Page<T> page)")
                .contains("public static <T> PageResponse<T> of(List<T> content");
    }

    @Test
    void hexagonalController_usesResponseEntityAndPageResponseFromList() {
        List<GeneratedUnit> units = generateHex();

        assertHasFile(units, "src/main/java/com/example/shop/common/PageResponse.java");

        String controller = readUnit(units, "src/main/java/com/example/shop/adapter/in/web/CustomerController.java");
        assertThat(controller).contains("ResponseEntity<CustomerResponse> create(");
        assertThat(controller).contains("ResponseEntity<PageResponse<CustomerResponse>> findAll(");
        assertThat(controller).contains("PageResponse.of(content, page, size, total)");
        assertThat(controller).contains("useCase.count()");

        String useCase = readUnit(units, "src/main/java/com/example/shop/application/usecase/CustomerUseCase.java");
        assertThat(useCase).contains("long count();");
    }

    @Test
    void dddController_usesResponseEntityAndPageResponse() {
        List<GeneratedUnit> units = generateDdd();

        assertHasFile(units, "src/main/java/com/example/shop/common/PageResponse.java");

        String controller = readUnit(units, "src/main/java/com/example/shop/interfaces/rest/CustomerController.java");
        assertThat(controller).contains("ResponseEntity<CustomerResponse> create(");
        assertThat(controller).contains("ResponseEntity<PageResponse<CustomerResponse>> findAll(");
        assertThat(controller).contains("service.count()");

        String appService = readUnit(units, "src/main/java/com/example/shop/application/customer/CustomerApplicationService.java");
        assertThat(appService).contains("public long count()");
    }

    // ---- helpers ----

    private static List<GeneratedUnit> generateMvc() {
        return new MvcGenerator(new TemplateEngine(null), ctx("mvc", "jpa")).generate(schema());
    }

    private static List<GeneratedUnit> generateHex() {
        return new HexagonalGenerator(new TemplateEngine(null), ctx("hexagonal", "jpa")).generate(schema());
    }

    private static List<GeneratedUnit> generateDdd() {
        return new DddGenerator(new TemplateEngine(null), ctx("ddd", "jpa")).generate(schema());
    }

    private static GeneratorContext ctx(String architecture, String persistence) {
        return new GeneratorContext(
                "com.example.shop", "shop-api", "com.example",
                "3.3.5", "17", true,
                architecture, persistence, "xml", false, "none", "constructor",
                "jakarta", "class", "separate", "problemdetail",
                UmabootConfig.AuditOptions.defaults(), UmabootConfig.SoftDeleteOptions.defaults(),
                UmabootConfig.DockerOptions.defaults(), UmabootConfig.CiOptions.defaults(), UmabootConfig.LoggingOptions.defaults(),
                UmabootConfig.TestOptions.defaults(), "offset", UmabootConfig.SecurityOptions.defaults(),
                UmabootConfig.DddOptions.defaults(),
                false, "postgres", null, null);
    }

    private static SchemaModel schema() {
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

    private static String readUnit(List<GeneratedUnit> units, String relativePath) {
        return units.stream()
                .filter(u -> u.relativePath().equals(relativePath))
                .findFirst()
                .map(GeneratedUnit::content)
                .orElseThrow(() -> new AssertionError("Missing: " + relativePath));
    }
}

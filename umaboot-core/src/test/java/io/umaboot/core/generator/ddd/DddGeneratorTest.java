package io.umaboot.core.generator.ddd;

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
 * Smoke test for the DDD generator — runs a synthetic schema through every
 * template and asserts the expected files were emitted with key markers.
 */
class DddGeneratorTest {

    @Test
    void emitsFullDddLayoutPerAggregate() {
        SchemaModel schema = singleTableSchema();
        GeneratorContext ctx = new GeneratorContext(
                "com.example.shop", "shop-api", "com.example",
                "3.3.5", "17", true,
                "ddd", "jpa", "xml", false, "none", "constructor",
                "jakarta", "class", "separate", "problemdetail",
                UmabootConfig.AuditOptions.defaults(), UmabootConfig.SoftDeleteOptions.defaults(),
                UmabootConfig.DockerOptions.defaults(), UmabootConfig.CiOptions.defaults(), UmabootConfig.LoggingOptions.defaults(),
                UmabootConfig.TestOptions.defaults(), "offset", UmabootConfig.SecurityOptions.defaults(),
                UmabootConfig.DddOptions.defaults(), false, "postgres", null, null, "", null, "maven");
        TemplateEngine engine = new TemplateEngine(null);

        List<GeneratedUnit> units = new DddGenerator(engine, ctx).generate(schema);

        // Project-wide
        assertHasFile(units, "pom.xml");
        assertHasFile(units, "src/main/resources/application.yml");
        assertHasFile(units, "src/main/java/com/example/shop/Application.java");
        assertHasFile(units, "src/main/java/com/example/shop/domain/shared/DomainEvent.java");
        assertHasFile(units, "src/main/java/com/example/shop/interfaces/rest/GlobalExceptionHandler.java");

        // Per aggregate (table 'customers' -> aggregate 'customer')
        String agg = "src/main/java/com/example/shop";
        assertHasFile(units, agg + "/domain/customer/Customer.java");
        assertHasFile(units, agg + "/domain/customer/CustomerRepository.java");
        assertHasFile(units, agg + "/domain/customer/CustomerNotFoundException.java");
        assertHasFile(units, agg + "/domain/customer/event/CustomerCreatedEvent.java");
        assertHasFile(units, agg + "/domain/customer/event/CustomerUpdatedEvent.java");
        assertHasFile(units, agg + "/application/customer/CustomerApplicationService.java");
        assertHasFile(units, agg + "/application/customer/command/CreateCustomerCommand.java");
        assertHasFile(units, agg + "/application/customer/command/UpdateCustomerCommand.java");
        assertHasFile(units, agg + "/interfaces/rest/CustomerController.java");
        assertHasFile(units, agg + "/interfaces/rest/dto/CreateCustomerRequest.java");
        assertHasFile(units, agg + "/interfaces/rest/dto/UpdateCustomerRequest.java");
        assertHasFile(units, agg + "/interfaces/rest/dto/CustomerResponse.java");
        assertHasFile(units, agg + "/interfaces/rest/mapper/CustomerWebMapper.java");
        assertHasFile(units, agg + "/infrastructure/persistence/CustomerJpaEntity.java");
        assertHasFile(units, agg + "/infrastructure/persistence/CustomerJpaRepository.java");
        assertHasFile(units, agg + "/infrastructure/persistence/CustomerRepositoryImpl.java");
        assertHasFile(units, agg + "/infrastructure/persistence/CustomerJpaMapper.java");

        // Spot-check key DDD content
        String agg_root = readUnit(units, agg + "/domain/customer/Customer.java");
        assertThat(agg_root).contains("public static Customer create(");
        assertThat(agg_root).contains("public void updateFrom(");
        assertThat(agg_root).contains("pullDomainEvents");
        assertThat(agg_root).contains("CustomerCreatedEvent");

        String appService = readUnit(units, agg + "/application/customer/CustomerApplicationService.java");
        assertThat(appService).contains("ApplicationEventPublisher");
        assertThat(appService).contains("Create" + "CustomerCommand");
        assertThat(appService).contains("@Transactional");

        String controller = readUnit(units, agg + "/interfaces/rest/CustomerController.java");
        assertThat(controller).contains("@RestController");
        assertThat(controller).contains("toCreateCommand");

        String repoImpl = readUnit(units, agg + "/infrastructure/persistence/CustomerRepositoryImpl.java");
        assertThat(repoImpl).contains("implements CustomerRepository");
    }

    @Test
    void respectsAggregateRootOverrides() {
        TableModel customers = simpleTable("customers");
        TableModel notes = simpleTable("notes");
        SchemaModel schema = new SchemaModel("public", List.of(customers, notes));

        GeneratorContext ctx = new GeneratorContext(
                "com.example.shop", "shop-api", "com.example",
                "3.3.5", "17", true,
                "ddd", "jpa", "xml", false, "none", "constructor",
                "jakarta", "class", "separate", "problemdetail",
                UmabootConfig.AuditOptions.defaults(), UmabootConfig.SoftDeleteOptions.defaults(),
                UmabootConfig.DockerOptions.defaults(), UmabootConfig.CiOptions.defaults(), UmabootConfig.LoggingOptions.defaults(),
                UmabootConfig.TestOptions.defaults(), "offset", UmabootConfig.SecurityOptions.defaults(),
                new UmabootConfig.DddOptions(
                        List.of("customers"), // only customers is a root
                        List.of(),
                        java.util.Map.of(),
                        "shared"),
                false, "postgres", null, null, "", null, "maven");

        TemplateEngine engine = new TemplateEngine(null);
        List<GeneratedUnit> units = new DddGenerator(engine, ctx).generate(schema);

        assertHasFile(units, "src/main/java/com/example/shop/domain/customer/Customer.java");
        assertNoFile(units, "src/main/java/com/example/shop/domain/note/Note.java");
    }

    // ---- helpers ----

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

    private static SchemaModel singleTableSchema() {
        return new SchemaModel("public", List.of(simpleTable("customers")));
    }

    private static TableModel simpleTable(String name) {
        ColumnModel id = new ColumnModel("id", Types.BIGINT, "int8", 19, 0,
                false, true, true, null, "", List.of());
        ColumnModel email = new ColumnModel("email", Types.VARCHAR, "varchar", 255, 0,
                false, false, false, null, "", List.of());
        ColumnModel fullName = new ColumnModel("full_name", Types.VARCHAR, "varchar", 200, 0,
                false, false, false, null, "", List.of());
        return new TableModel(name, "public", "",
                List.of(id, email, fullName),
                List.of("id"),
                List.of(),
                List.of(),
                false);
    }
}

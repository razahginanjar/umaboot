package io.umaboot.core.generator;

import io.umaboot.core.config.UmabootConfig;
import io.umaboot.core.generator.ddd.DddGenerator;
import io.umaboot.core.generator.hexagonal.HexagonalGenerator;
import io.umaboot.core.model.ColumnModel;
import io.umaboot.core.model.SchemaModel;
import io.umaboot.core.model.TableModel;
import io.umaboot.core.template.TemplateEngine;
import org.junit.jupiter.api.Test;

import java.sql.Types;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the Hex+jOOQ and DDD+jOOQ combinations introduced in this branch.
 *
 * <p>Pattern A: the persistence adapter / repository implementation uses jOOQ's
 * {@code DSLContext} and {@code Tables.{TABLE}} static refs directly. The
 * domain model (Hex) or aggregate root (DDD) stays jOOQ-free; jOOQ's record
 * mapping happens entirely inside the adapter package.</p>
 */
class HexagonalAndDddJooqRenderTest {

    // ---------------------------------------------------------------- Hex + jOOQ

    @Test
    void hexagonalJooq_emitsAdapterAndJooqPom() {
        List<GeneratedUnit> units = generateHex("jooq");

        String javaSrc = "src/main/java/com/example/shop";
        // Domain layer untouched — same files as Hex+JPA / Hex+MyBatis
        assertHasFile(units, javaSrc + "/domain/model/Customer.java");
        assertHasFile(units, javaSrc + "/domain/port/CustomerRepository.java");
        // jOOQ adapter is the SOLE persistence file emitted (no JpaEntity, no PersistenceModel)
        assertHasFile(units, javaSrc + "/adapter/out/persistence/CustomerPersistenceAdapter.java");
        assertNoFile(units, javaSrc + "/adapter/out/persistence/CustomerJpaEntity.java");
        assertNoFile(units, javaSrc + "/adapter/out/persistence/CustomerPersistenceModel.java");
        assertNoFile(units, javaSrc + "/adapter/out/persistence/CustomerMyBatisMapper.java");

        String adapter = readUnit(units, javaSrc + "/adapter/out/persistence/CustomerPersistenceAdapter.java");
        // Implements the domain port
        assertThat(adapter).contains("implements CustomerRepository");
        // Uses DSLContext + Tables static refs
        assertThat(adapter).contains("import org.jooq.DSLContext;");
        assertThat(adapter).contains("import static com.example.shop.jooq.Tables.CUSTOMERS;");
        // Uses fetchInto for record→domain (Hex domain has setters)
        assertThat(adapter).contains("fetchOptionalInto(Customer.class)");
        assertThat(adapter).contains("fetchInto(Customer.class)");
    }

    @Test
    void hexagonalJooq_pomHasJooqStarterAndCodegenPlugin() {
        String pom = readUnit(generateHex("jooq"), "pom.xml");

        // jOOQ runtime + codegen plugin
        assertThat(pom).contains("spring-boot-starter-jooq");
        assertThat(pom).contains("jooq-codegen-maven");
        assertThat(pom).contains("PostgresDatabase");
        assertThat(pom).contains("<packageName>com.example.shop.jooq</packageName>");
        // No JPA / MyBatis cruft
        assertThat(pom).doesNotContain("spring-boot-starter-data-jpa");
        assertThat(pom).doesNotContain("mybatis-spring-boot-starter");
        // spring-data-commons is needed because PageResponse imports Page
        assertThat(pom).contains("<artifactId>spring-data-commons</artifactId>");
    }

    // ---------------------------------------------------------------- DDD + jOOQ

    @Test
    void dddJooq_emitsRepositoryImplAndAggregateReconstructionConstructor() {
        List<GeneratedUnit> units = generateDdd("jooq");

        String agg = "src/main/java/com/example/shop";
        // Domain (aggregate root) — has the new reconstruction constructor
        String aggregate = readUnit(units, agg + "/domain/customer/Customer.java");
        // Public reconstruction ctor is added unconditionally (used by jOOQ here, harmless elsewhere)
        assertThat(aggregate).contains("public Customer(");
        assertThat(aggregate).contains("Reconstruction constructor");

        // Persistence layer: only RepositoryImpl is emitted for jOOQ — no JpaEntity, no MyBatis mapper
        assertHasFile(units, agg + "/infrastructure/persistence/CustomerRepositoryImpl.java");
        assertNoFile(units, agg + "/infrastructure/persistence/CustomerJpaEntity.java");
        assertNoFile(units, agg + "/infrastructure/persistence/CustomerPersistenceModel.java");
        assertNoFile(units, agg + "/infrastructure/persistence/CustomerMyBatisMapper.java");

        String repo = readUnit(units, agg + "/infrastructure/persistence/CustomerRepositoryImpl.java");
        assertThat(repo).contains("implements CustomerRepository");
        assertThat(repo).contains("import org.jooq.DSLContext;");
        assertThat(repo).contains("import static com.example.shop.jooq.Tables.CUSTOMERS;");
        // Uses the reconstruction constructor, NOT the create() factory which would record events
        assertThat(repo).contains("new Customer(");
        assertThat(repo).doesNotContain("Customer.create(");
        assertThat(repo).contains("toAggregate"); // the private helper
    }

    @Test
    void dddJooq_pomHasJooqStarterAndCodegenPlugin() {
        String pom = readUnit(generateDdd("jooq"), "pom.xml");
        assertThat(pom).contains("spring-boot-starter-jooq");
        assertThat(pom).contains("jooq-codegen-maven");
        assertThat(pom).doesNotContain("spring-boot-starter-data-jpa");
        assertThat(pom).doesNotContain("mybatis-spring-boot-starter");
        assertThat(pom).contains("<artifactId>spring-data-commons</artifactId>");
    }

    // ---------------------------------------------------------------- helpers

    private static List<GeneratedUnit> generateHex(String persistence) {
        GeneratorContext ctx = ctx("hexagonal", persistence);
        return new HexagonalGenerator(new TemplateEngine(null), ctx).generate(schema());
    }

    private static List<GeneratedUnit> generateDdd(String persistence) {
        GeneratorContext ctx = ctx("ddd", persistence);
        return new DddGenerator(new TemplateEngine(null), ctx).generate(schema());
    }

    private static GeneratorContext ctx(String architecture, String persistence) {
        return new GeneratorContext(
                "com.example.shop", "shop-api", "com.example",
                "3.3.5", "17", true,
                architecture, persistence, "xml", false, "none", "constructor",
                "jakarta", "class", "separate", "problemdetail",
                UmabootConfig.AuditOptions.defaults(), UmabootConfig.SoftDeleteOptions.defaults(),
                UmabootConfig.DockerOptions.defaults(), UmabootConfig.CiOptions.defaults(),
                UmabootConfig.LoggingOptions.defaults(), UmabootConfig.TestOptions.defaults(),
                "offset", UmabootConfig.SecurityOptions.defaults(),
                UmabootConfig.DddOptions.defaults(),
                false, "postgres", null, null, "", null, "maven");
    }

    private static SchemaModel schema() {
        ColumnModel id = new ColumnModel("id", Types.BIGINT, "int8", 19, 0,
                false, true, true, null, "", List.of());
        ColumnModel email = new ColumnModel("email", Types.VARCHAR, "varchar", 255, 0,
                false, false, false, null, "", List.of());
        TableModel customers = new TableModel("customers", "public", "",
                List.of(id, email),
                List.of("id"),
                List.of(),
                List.of(),
                false);
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

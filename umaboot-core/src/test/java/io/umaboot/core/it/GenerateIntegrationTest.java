package io.umaboot.core.it;

import io.umaboot.core.architecture.layered.LayeredArchitectureRenderer;
import io.umaboot.core.generator.GeneratedUnit;
import io.umaboot.core.generator.GeneratorContext;
import io.umaboot.core.generator.mvc.MvcGenerator;
import io.umaboot.core.introspection.postgres.PostgresIntrospector;
import io.umaboot.core.model.RelationshipType;
import io.umaboot.core.model.SchemaModel;
import io.umaboot.core.relationship.RelationshipEngine;
import io.umaboot.core.template.TemplateEngine;
import io.umaboot.fixtures.FixtureLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test:
 * <ol>
 *   <li>Spin up a Postgres container.</li>
 *   <li>Apply the fixture schema.</li>
 *   <li>Introspect, run the relationship engine.</li>
 *   <li>Generate the project, render to a temp dir.</li>
 *   <li>Assert key files were produced and contain expected content.</li>
 * </ol>
 */
@Testcontainers
class GenerateIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("Umaboot")
            .withUsername("postgres")
            .withPassword("postgres");

    @Test
    void introspectsAndGeneratesProject(@TempDir Path output) throws Exception {
        applyFixture();

        SchemaModel schema;
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            schema = new PostgresIntrospector(conn).introspect("public");
        }
        schema = new RelationshipEngine().analyze(schema);

        // Sanity: schema content
        assertThat(schema.tables()).extracting("name")
                .contains("customers", "addresses", "products", "orders", "order_items",
                        "tags", "product_tags");

        // Sanity: relationships
        var products = schema.findTable("products");
        assertThat(products.relationships())
                .anyMatch(r -> r.type() instanceof RelationshipType.ManyToMany
                        && r.toTable().equals("tags"));
        assertThat(schema.findTable("product_tags").junction()).isTrue();

        // Generate
        GeneratorContext ctx = GeneratorContext.defaults("com.example.shop", "shop-api");
        TemplateEngine engine = new TemplateEngine(null);
        List<GeneratedUnit> units = new MvcGenerator(engine, ctx).generate(schema);
        new LayeredArchitectureRenderer().render(units, output);

        // Verify expected files
        Path javaSrc = output.resolve("src/main/java/com/example/shop");
        assertThat(Files.exists(javaSrc.resolve("Application.java"))).isTrue();
        assertThat(Files.exists(javaSrc.resolve("entity/Customer.java"))).isTrue();
        assertThat(Files.exists(javaSrc.resolve("repository/CustomerRepository.java"))).isTrue();
        assertThat(Files.exists(javaSrc.resolve("controller/CustomerController.java"))).isTrue();
        assertThat(Files.exists(javaSrc.resolve("entity/Product.java"))).isTrue();
        assertThat(Files.exists(output.resolve("pom.xml"))).isTrue();

        // Junction table should NOT have its own entity
        assertThat(Files.exists(javaSrc.resolve("entity/ProductTag.java"))).isFalse();

        // Spot-check entity content for ManyToMany on Product
        String productEntity = Files.readString(javaSrc.resolve("entity/Product.java"));
        assertThat(productEntity).contains("@ManyToMany");
        assertThat(productEntity).contains("@JoinTable(name = \"product_tags\"");

        // Spot-check Customer entity for self-reference and 1:N inverse
        String customerEntity = Files.readString(javaSrc.resolve("entity/Customer.java"));
        assertThat(customerEntity).contains("@ManyToOne");
        assertThat(customerEntity).contains("@OneToMany");
    }

    @Test
    void rejectsNonExistentSchema_withClearMessage() throws Exception {
        applyFixture();

        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var introspector = new PostgresIntrospector(conn);
            org.assertj.core.api.Assertions
                    .assertThatThrownBy(() -> introspector.introspect("does_not_exist"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("does not exist")
                    .hasMessageContaining("does_not_exist");
        }
    }

    private static void applyFixture() throws Exception {
        String sql = FixtureLoader.load(FixtureLoader.POSTGRES_SAMPLE);
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }
}

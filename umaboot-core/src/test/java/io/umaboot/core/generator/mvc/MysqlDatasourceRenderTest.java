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
 * Regression tests for the v0.8 datasource-config fix. Generating against a MySQL
 * connection must produce MySQL-flavored {@code application.yml} + {@code pom.xml},
 * with credentials taken from the umaboot.yaml connection block and wrapped in
 * {@code ${SPRING_DATASOURCE_*}} env-var fallbacks.
 */
class MysqlDatasourceRenderTest {

    @Test
    void mysqlConnection_yieldsMysqlDriverAndDep_inMvcJpa() {
        UmabootConfig.Connection mysqlConn = new UmabootConfig.Connection(
                "host", "mysql",
                "db.example.com:3306", "useSSL=false",
                /* url   */ null,
                /* db    */ "orders",
                /* schema*/ "",
                "appuser", "s3cret", null);

        List<GeneratedUnit> units = generateWithConnection(mysqlConn);

        // application.yml — MySQL driver, real credentials wrapped in env-var fallbacks
        String yml = readUnit(units, "src/main/resources/application.yml");
        assertThat(yml).contains("driver-class-name: com.mysql.cj.jdbc.Driver");
        assertThat(yml).contains("${SPRING_DATASOURCE_URL:jdbc:mysql://db.example.com:3306/orders?useSSL=false}");
        assertThat(yml).contains("${SPRING_DATASOURCE_USERNAME:appuser}");
        assertThat(yml).contains("${SPRING_DATASOURCE_PASSWORD:s3cret}");
        // Postgres URL prefix and driver class must NOT appear
        assertThat(yml).doesNotContain("jdbc:postgresql:");
        assertThat(yml).doesNotContain("org.postgresql.Driver");
        // default_schema is Postgres-only — should not appear for MySQL
        assertThat(yml).doesNotContain("default_schema");

        // pom.xml — mysql-connector-j runtime dep, NOT postgresql
        String pom = readUnit(units, "pom.xml");
        assertThat(pom).contains("<artifactId>mysql-connector-j</artifactId>");
        assertThat(pom).doesNotContain("<artifactId>postgresql</artifactId>");
    }

    @Test
    void postgresConnection_keepsPostgresDriverAndDep_inMvcJpa() {
        UmabootConfig.Connection pgConn = new UmabootConfig.Connection(
                "host", "postgresql",
                "localhost:5432", "",
                /* url   */ null,
                /* db    */ "shop",
                /* schema*/ "public",
                "postgres", "postgres", null);

        List<GeneratedUnit> units = generateWithConnection(pgConn);

        String yml = readUnit(units, "src/main/resources/application.yml");
        assertThat(yml).contains("driver-class-name: org.postgresql.Driver");
        assertThat(yml).contains("${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/shop}");
        // default_schema only for postgres
        assertThat(yml).contains("default_schema: public");

        String pom = readUnit(units, "pom.xml");
        assertThat(pom).contains("<artifactId>postgresql</artifactId>");
        assertThat(pom).doesNotContain("<artifactId>mysql-connector-j</artifactId>");
    }

    @Test
    void mysqlConnection_jooqCodegenPluginUsesMySQLDatabase() {
        UmabootConfig.Connection mysqlConn = new UmabootConfig.Connection(
                "host", "mysql",
                "db.example.com:3306", "",
                null,
                "orders", "",
                "appuser", "s3cret", null);

        List<GeneratedUnit> units = generateWithConnection(mysqlConn, "jooq");

        String pom = readUnit(units, "pom.xml");
        // jOOQ codegen plugin must use the MySQL meta-database class for a MySQL project
        assertThat(pom).contains("org.jooq.meta.mysql.MySQLDatabase");
        assertThat(pom).doesNotContain("org.jooq.meta.postgres.PostgresDatabase");
        // The plugin's <jdbc> block uses the MySQL driver + URL from the connection
        assertThat(pom).contains("<driver>com.mysql.cj.jdbc.Driver</driver>");
        assertThat(pom).contains("<url>jdbc:mysql://db.example.com:3306/orders</url>");
    }

    // ---- helpers ----

    private static List<GeneratedUnit> generateWithConnection(UmabootConfig.Connection conn) {
        return generateWithConnection(conn, "jpa");
    }

    private static List<GeneratedUnit> generateWithConnection(UmabootConfig.Connection conn, String persistence) {
        SchemaModel schema = singleTableSchema(conn.schema().isEmpty() ? "public" : conn.schema());
        GeneratorContext ctx = new GeneratorContext(
                "com.example.shop", "shop-api", "com.example",
                "3.3.5", "17", true,
                "mvc", persistence, "xml", false, "none", "constructor",
                "jakarta", "class", "separate", "problemdetail",
                UmabootConfig.AuditOptions.defaults(), UmabootConfig.SoftDeleteOptions.defaults(),
                UmabootConfig.DockerOptions.defaults(), UmabootConfig.CiOptions.defaults(), UmabootConfig.LoggingOptions.defaults(),
                UmabootConfig.TestOptions.defaults(), "offset", UmabootConfig.SecurityOptions.defaults(),
                UmabootConfig.DddOptions.defaults(), false, conn.driver(), conn, null, "", null, "maven");
        return new MvcGenerator(new TemplateEngine(null), ctx).generate(schema);
    }

    private static SchemaModel singleTableSchema(String schemaName) {
        ColumnModel id = new ColumnModel("id", Types.BIGINT, "int8", 19, 0,
                false, true, true, null, "", List.of());
        ColumnModel name = new ColumnModel("name", Types.VARCHAR, "varchar", 255, 0,
                false, false, false, null, "", List.of());
        TableModel customers = new TableModel("customers", schemaName, "",
                List.of(id, name),
                List.of("id"),
                List.of(),
                List.of(),
                false);
        return new SchemaModel(schemaName, List.of(customers));
    }

    private static String readUnit(List<GeneratedUnit> units, String relativePath) {
        return units.stream()
                .filter(u -> u.relativePath().equals(relativePath))
                .findFirst()
                .map(GeneratedUnit::content)
                .orElseThrow(() -> new AssertionError("Missing: " + relativePath));
    }
}

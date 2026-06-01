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
 * Locks the MariaDB target branches added in the sql-file-source feature:
 *
 * <ul>
 *   <li>pom uses {@code mariadb-java-client} runtime dep, not mysql-connector-j or postgresql</li>
 *   <li>application.yml uses {@code jdbc:mariadb://} URL and {@code org.mariadb.jdbc.Driver}</li>
 *   <li>docker-compose uses {@code mariadb:11} image with {@code MARIADB_*} env vars</li>
 *   <li>Testcontainers integration test scaffolding uses {@code MariaDBContainer}</li>
 * </ul>
 *
 * <p>MariaDB rides MySQL's coattails for DDL parsing + introspection (same parser
 * path, MysqlIntrospector handles both via the JDBC driver), but has distinct
 * runtime artifacts.</p>
 */
class MariadbTargetRenderTest {

    @Test
    void mariadb_pomEmitsMariadbClient_notMysqlConnector() {
        String pom = readUnit(generate("mariadb"), "pom.xml");

        assertThat(pom).contains("<artifactId>mariadb-java-client</artifactId>");
        assertThat(pom).doesNotContain("<artifactId>mysql-connector-j</artifactId>");
        assertThat(pom).doesNotContain("<artifactId>postgresql</artifactId>");
    }

    @Test
    void mariadb_applicationYmlUsesMariadbDriverAndUrl() {
        String yml = readUnit(generate("mariadb"), "src/main/resources/application.yml");

        assertThat(yml).contains("jdbc:mariadb://");
        assertThat(yml).contains("org.mariadb.jdbc.Driver");
        assertThat(yml).doesNotContain("com.mysql.cj.jdbc.Driver");
        assertThat(yml).doesNotContain("org.postgresql.Driver");
    }

    @Test
    void mariadb_dockerComposeUsesMariadbImage() {
        var units = generateWithDocker("mariadb");
        String compose = readUnit(units, "docker-compose.yml");

        assertThat(compose).contains("image: mariadb:11");
        assertThat(compose).contains("MARIADB_DATABASE");
        assertThat(compose).contains("jdbc:mariadb://db:3306");
        assertThat(compose).doesNotContain("postgres:16");
    }

    @Test
    void mysql_unaffected_stillUsesMysqlConnector() {
        // Regression: the 3-way switch shouldn't break the MySQL path.
        String pom = readUnit(generate("mysql"), "pom.xml");
        assertThat(pom).contains("<artifactId>mysql-connector-j</artifactId>");
        assertThat(pom).doesNotContain("mariadb-java-client");
    }

    @Test
    void postgres_unaffected_stillUsesPostgresJdbc() {
        // Regression: same for Postgres.
        String pom = readUnit(generate("postgresql"), "pom.xml");
        assertThat(pom).contains("<artifactId>postgresql</artifactId>");
        assertThat(pom).doesNotContain("mariadb-java-client");
        assertThat(pom).doesNotContain("mysql-connector-j");
    }

    // ============================================================ helpers

    private static List<GeneratedUnit> generate(String dbDriver) {
        GeneratorContext ctx = ctx(dbDriver, false);
        return new MvcGenerator(new TemplateEngine(null), ctx).generate(schema());
    }

    private static List<GeneratedUnit> generateWithDocker(String dbDriver) {
        GeneratorContext ctx = ctx(dbDriver, true);
        return new MvcGenerator(new TemplateEngine(null), ctx).generate(schema());
    }

    private static GeneratorContext ctx(String dbDriver, boolean docker) {
        return new GeneratorContext(
                "com.example.app", "app", "com.example",
                "3.3.5", "17", true,
                "mvc", "jpa", "xml", false, "none", "constructor",
                "jakarta", "class", "separate", "problemdetail",
                UmabootConfig.AuditOptions.defaults(),
                UmabootConfig.SoftDeleteOptions.defaults(),
                docker ? new UmabootConfig.DockerOptions(true, "eclipse-temurin:17-jre-alpine", 8080)
                       : UmabootConfig.DockerOptions.defaults(),
                UmabootConfig.CiOptions.defaults(),
                UmabootConfig.LoggingOptions.defaults(),
                UmabootConfig.TestOptions.defaults(),
                "offset",
                UmabootConfig.SecurityOptions.defaults(),
                UmabootConfig.DddOptions.defaults(),
                false, dbDriver, null, null, "", null, "maven");
    }

    private static SchemaModel schema() {
        ColumnModel id = new ColumnModel("id", Types.BIGINT, "bigint", 19, 0,
                false, true, true, null, "", List.of());
        ColumnModel name = new ColumnModel("name", Types.VARCHAR, "varchar", 100, 0,
                false, false, false, null, "", List.of());
        TableModel users = new TableModel("users", "public", "",
                List.of(id, name),
                List.of("id"),
                List.of(),
                List.of(),
                false);
        return new SchemaModel("public", List.of(users));
    }

    private static String readUnit(List<GeneratedUnit> units, String path) {
        return units.stream()
                .filter(u -> u.relativePath().equals(path))
                .findFirst()
                .map(GeneratedUnit::content)
                .orElseThrow(() -> new AssertionError("Missing: " + path));
    }
}

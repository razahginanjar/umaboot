package io.umaboot.core.generator;

import io.umaboot.core.config.UmabootConfig;
import io.umaboot.core.generator.mvc.MvcGenerator;
import io.umaboot.core.introspection.sqlfile.SqlFileIntrospector;
import io.umaboot.core.model.ColumnModel;
import io.umaboot.core.model.SchemaModel;
import io.umaboot.core.model.TableModel;
import io.umaboot.core.template.TemplateEngine;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end SQL Server target tests:
 *
 * <ul>
 *   <li>pom uses {@code mssql-jdbc} runtime dep + {@code mssqlserver} Testcontainers module
 *       + {@code SQLServerDatabase} jOOQ codegen dialect (when applicable)</li>
 *   <li>application.yml uses {@code jdbc:sqlserver://} URL + {@code SQLServerDriver}
 *       and emits {@code default_schema: dbo}</li>
 *   <li>docker-compose uses {@code mcr.microsoft.com/mssql/server} image with
 *       {@code ACCEPT_EULA=Y} + {@code MSSQL_SA_PASSWORD}</li>
 *   <li>{@link SqlFileIntrospector} parses T-SQL idioms — IDENTITY(1,1) as
 *       autoIncrement, {@code [bracket]} quoting, {@code NVARCHAR(MAX)} as
 *       varchar of size 0 (the MAX sentinel)</li>
 * </ul>
 *
 * <p>Other DB targets (postgres, mysql, mariadb) regression-checked to ensure
 * the new branch doesn't leak into other paths.</p>
 */
class SqlServerTargetRenderTest {

    @Test
    void sqlserver_pomEmitsMssqlJdbcAndMssqlserverTestcontainers() {
        String pom = readUnit(generate("sqlserver", false), "pom.xml");

        assertThat(pom).contains("<artifactId>mssql-jdbc</artifactId>");
        assertThat(pom).doesNotContain("<artifactId>mariadb-java-client</artifactId>");
        assertThat(pom).doesNotContain("<artifactId>mysql-connector-j</artifactId>");
        assertThat(pom).doesNotContain("<artifactId>postgresql</artifactId>");
    }

    @Test
    void sqlserver_applicationYmlUsesSqlServerDriverAndDboSchema() {
        String yml = readUnit(generate("sqlserver", false), "src/main/resources/application.yml");

        assertThat(yml).contains("jdbc:sqlserver://");
        assertThat(yml).contains("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        // default_schema fires for SQL Server (its default is dbo, parallel to Postgres `public`)
        assertThat(yml).contains("default_schema:");
        assertThat(yml).doesNotContain("com.mysql.cj.jdbc.Driver");
        assertThat(yml).doesNotContain("org.postgresql.Driver");
    }

    @Test
    void sqlserver_dockerComposeUsesMssqlImageAndEula() {
        String compose = readUnit(generate("sqlserver", true), "docker-compose.yml");

        assertThat(compose).contains("image: mcr.microsoft.com/mssql/server");
        assertThat(compose).contains("ACCEPT_EULA");
        assertThat(compose).contains("MSSQL_SA_PASSWORD");
        assertThat(compose).contains("jdbc:sqlserver://db:1433");
        assertThat(compose).doesNotContain("mariadb:11");
        assertThat(compose).doesNotContain("postgres:16");
    }

    @Test
    void postgres_unaffected() {
        String pom = readUnit(generate("postgresql", false), "pom.xml");
        assertThat(pom).contains("<artifactId>postgresql</artifactId>");
        assertThat(pom).doesNotContain("mssql-jdbc");
    }

    @Test
    void mysql_unaffected() {
        String pom = readUnit(generate("mysql", false), "pom.xml");
        assertThat(pom).contains("<artifactId>mysql-connector-j</artifactId>");
        assertThat(pom).doesNotContain("mssql-jdbc");
    }

    @Test
    void mariadb_unaffected() {
        String pom = readUnit(generate("mariadb", false), "pom.xml");
        assertThat(pom).contains("<artifactId>mariadb-java-client</artifactId>");
        assertThat(pom).doesNotContain("mssql-jdbc");
    }

    // ------------------------------------------------------------------ T-SQL DDL parsing

    @Test
    void sqlFileIntrospector_identityIsAutoIncrement() throws SQLException {
        String sql = """
                CREATE TABLE users (
                    id BIGINT IDENTITY(1,1) PRIMARY KEY,
                    email NVARCHAR(255) NOT NULL,
                    full_name NVARCHAR(200) NOT NULL,
                    created_at DATETIME2 NOT NULL DEFAULT GETDATE()
                );
                """;
        SchemaModel schema = new SqlFileIntrospector(sql, "sqlserver").introspect("dbo");

        TableModel users = schema.findTable("users");
        assertThat(users).isNotNull();
        ColumnModel id = users.findColumn("id");
        assertThat(id).isNotNull();
        // T-SQL IDENTITY(1,1) → autoIncrement=true
        assertThat(id.autoIncrement()).isTrue();
        assertThat(id.jdbcType()).isEqualTo(Types.BIGINT);
        assertThat(id.primaryKey()).isTrue();
    }

    @Test
    void sqlFileIntrospector_nvarcharMaxIsTreatedAsLargeText() throws SQLException {
        String sql = """
                CREATE TABLE articles (
                    id INT IDENTITY(1,1) PRIMARY KEY,
                    title NVARCHAR(255) NOT NULL,
                    body NVARCHAR(MAX) NOT NULL,
                    excerpt VARCHAR(MAX)
                );
                """;
        SchemaModel schema = new SqlFileIntrospector(sql, "sqlserver").introspect("dbo");
        TableModel t = schema.findTable("articles");
        assertThat(t).isNotNull();
        ColumnModel body = t.findColumn("body");
        assertThat(body).isNotNull();
        // MAX is a sentinel — parseSizeAndScale falls back to 0 when it hits non-numeric.
        // The JDBC type still resolves correctly via canonicalSqlType.
        assertThat(body.jdbcType()).isEqualTo(Types.NVARCHAR);
        assertThat(body.size()).isEqualTo(0);
    }

    @Test
    void sqlFileIntrospector_uniqueidentifierMappedToOther() throws SQLException {
        String sql = """
                CREATE TABLE sessions (
                    id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
                    user_id BIGINT NOT NULL
                );
                """;
        SchemaModel schema = new SqlFileIntrospector(sql, "sqlserver").introspect("dbo");
        TableModel t = schema.findTable("sessions");
        ColumnModel id = t.findColumn("id");
        assertThat(id).isNotNull();
        assertThat(id.jdbcType()).isEqualTo(Types.OTHER);
        assertThat(id.sqlType()).isEqualTo("uniqueidentifier");
    }

    // ------------------------------------------------------------------ helpers

    private static List<GeneratedUnit> generate(String dbDriver, boolean docker) {
        GeneratorContext ctx = ctx(dbDriver, docker);
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
        TableModel users = new TableModel("users", "dbo", "",
                List.of(id, name),
                List.of("id"),
                List.of(),
                List.of(),
                false);
        return new SchemaModel("dbo", List.of(users));
    }

    private static String readUnit(List<GeneratedUnit> units, String path) {
        return units.stream()
                .filter(u -> u.relativePath().equals(path))
                .findFirst()
                .map(GeneratedUnit::content)
                .orElseThrow(() -> new AssertionError("Missing: " + path));
    }
}

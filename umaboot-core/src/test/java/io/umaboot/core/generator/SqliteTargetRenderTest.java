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
 * End-to-end SQLite target tests:
 *
 * <ul>
 *   <li>pom uses {@code sqlite-jdbc} runtime dep + {@code hibernate-community-dialects}
 *       (when JPA, since Hibernate has no built-in SQLite dialect)</li>
 *   <li>pom skips the Testcontainers block entirely — there's no SQLite container</li>
 *   <li>application.yml uses {@code jdbc:sqlite:./...} URL + {@code org.sqlite.JDBC} driver
 *       and emits the explicit {@code hibernate.dialect: SQLiteDialect} override (Spring
 *       Boot doesn't auto-resolve community dialects)</li>
 *   <li>application.yml does NOT emit {@code default_schema:} (SQLite has no schemas)</li>
 *   <li>docker-compose has only the app service (no db service) since the engine is
 *       embedded; mounts {@code db-data:/data} so the .db file survives restarts</li>
 *   <li>AbstractIntegrationTest uses {@code @DynamicPropertySource} pointing at
 *       {@code jdbc:sqlite::memory:} with {@code create-drop} ddl-auto, no
 *       {@code @Testcontainers}</li>
 *   <li>{@link SqlFileIntrospector} treats {@code INTEGER PRIMARY KEY} as auto-increment
 *       (SQLite rowid alias) when dialectHint=sqlite, but NOT for other dialects</li>
 * </ul>
 *
 * <p>Other DB targets regression-checked.</p>
 */
class SqliteTargetRenderTest {

    @Test
    void sqlite_pomEmitsSqliteJdbc_andCommunityDialectsForJpa() {
        String pom = readUnit(generate("sqlite", "jpa", false, false), "pom.xml");

        assertThat(pom).contains("<artifactId>sqlite-jdbc</artifactId>");
        assertThat(pom).contains("<artifactId>hibernate-community-dialects</artifactId>");
        assertThat(pom).doesNotContain("mariadb-java-client");
        assertThat(pom).doesNotContain("mysql-connector-j");
        assertThat(pom).doesNotContain("mssql-jdbc");
        assertThat(pom).doesNotContain("<artifactId>postgresql</artifactId>");
    }

    @Test
    void sqlite_pomSkipsCommunityDialectsForMyBatis() {
        // hibernate-community-dialects is JPA-only; MyBatis users don't need it.
        String pom = readUnit(generate("sqlite", "mybatis", false, false), "pom.xml");

        assertThat(pom).contains("<artifactId>sqlite-jdbc</artifactId>");
        assertThat(pom).doesNotContain("hibernate-community-dialects");
    }

    @Test
    void sqlite_pomSkipsTestcontainersBlock() {
        // testsEnabled=true would normally pull Testcontainers in — but SQLite has no
        // container image. The pom should NOT include the Testcontainers deps.
        String pom = readUnit(generate("sqlite", "jpa", false, true), "pom.xml");

        assertThat(pom).doesNotContain("testcontainers");
    }

    @Test
    void sqlite_applicationYmlUsesSqliteDriverAndDialectOverride() {
        String yml = readUnit(generate("sqlite", "jpa", false, false), "src/main/resources/application.yml");

        assertThat(yml).contains("jdbc:sqlite:");
        assertThat(yml).contains("org.sqlite.JDBC");
        // Community dialect override — Spring Boot doesn't auto-resolve it.
        assertThat(yml).contains("dialect: org.hibernate.community.dialect.SQLiteDialect");
        // SQLite has no schemas — default_schema must NOT be emitted.
        assertThat(yml).doesNotContain("default_schema:");
    }

    @Test
    void sqlite_dockerComposeIsAppOnly_noDbService() {
        String compose = readUnit(generate("sqlite", "jpa", true, false), "docker-compose.yml");

        // SQLite is embedded — no separate db container.
        assertThat(compose).doesNotContain("image: postgres");
        assertThat(compose).doesNotContain("image: mysql");
        assertThat(compose).doesNotContain("image: mariadb");
        assertThat(compose).doesNotContain("image: mcr.microsoft.com/mssql/server");
        // App still runs and mounts a persistent volume for the .db file.
        assertThat(compose).contains("jdbc:sqlite:");
        assertThat(compose).contains("/data");
        assertThat(compose).contains("db-data:");
        // No depends_on for sqlite (there's no db service to wait for).
        assertThat(compose).doesNotContain("depends_on:");
    }

    @Test
    void sqlite_abstractIntegrationTestUsesInMemoryNoTestcontainers() {
        String it = readUnit(generate("sqlite", "jpa", false, true),
                "src/test/java/com/example/app/AbstractIntegrationTest.java");

        assertThat(it).contains("jdbc:sqlite::memory:");
        assertThat(it).contains("org.hibernate.community.dialect.SQLiteDialect");
        assertThat(it).contains("create-drop");
        assertThat(it).doesNotContain("@Testcontainers");
        assertThat(it).doesNotContain("@Container");
        assertThat(it).doesNotContain("PostgreSQLContainer");
    }

    @Test
    void postgres_unaffected() {
        String pom = readUnit(generate("postgresql", "jpa", false, false), "pom.xml");
        assertThat(pom).contains("<artifactId>postgresql</artifactId>");
        assertThat(pom).doesNotContain("sqlite-jdbc");
        assertThat(pom).doesNotContain("hibernate-community-dialects");
    }

    @Test
    void sqlserver_unaffected() {
        String pom = readUnit(generate("sqlserver", "jpa", false, false), "pom.xml");
        assertThat(pom).contains("<artifactId>mssql-jdbc</artifactId>");
        assertThat(pom).doesNotContain("sqlite-jdbc");
    }

    // ============================================================ DDL parsing

    @Test
    void sqlFileIntrospector_integerPkIsAutoIncrement_whenDialectIsSqlite() throws SQLException {
        // SQLite rowid alias: any single-column INTEGER PRIMARY KEY auto-increments,
        // even without the AUTOINCREMENT keyword.
        String sql = """
                CREATE TABLE notes (
                    id INTEGER PRIMARY KEY,
                    title TEXT NOT NULL,
                    body TEXT
                );
                """;
        SchemaModel schema = new SqlFileIntrospector(sql, "sqlite").introspect("main");
        TableModel notes = schema.findTable("notes");
        assertThat(notes).isNotNull();
        ColumnModel id = notes.findColumn("id");
        assertThat(id).isNotNull();
        assertThat(id.autoIncrement()).isTrue();
        assertThat(id.primaryKey()).isTrue();
    }

    @Test
    void sqlFileIntrospector_integerPkNotAutoIncrement_whenDialectIsPostgres() throws SQLException {
        // The same DDL under postgres dialect must NOT flag autoIncrement —
        // postgres needs SERIAL / GENERATED BY DEFAULT AS IDENTITY.
        String sql = """
                CREATE TABLE notes (
                    id INTEGER PRIMARY KEY,
                    title TEXT NOT NULL
                );
                """;
        SchemaModel schema = new SqlFileIntrospector(sql, "postgresql").introspect("public");
        ColumnModel id = schema.findTable("notes").findColumn("id");
        assertThat(id.autoIncrement()).isFalse();
    }

    @Test
    void sqlFileIntrospector_tableLevelPrimaryKeyAlsoAutoIncrement_whenSqlite() throws SQLException {
        // The table-level PRIMARY KEY (id) form should still trigger the rowid
        // affinity rule under SQLite.
        String sql = """
                CREATE TABLE notes (
                    id INTEGER NOT NULL,
                    title TEXT NOT NULL,
                    PRIMARY KEY (id)
                );
                """;
        SchemaModel schema = new SqlFileIntrospector(sql, "sqlite").introspect("main");
        ColumnModel id = schema.findTable("notes").findColumn("id");
        assertThat(id.autoIncrement()).isTrue();
        assertThat(id.primaryKey()).isTrue();
    }

    @Test
    void sqlFileIntrospector_explicitAutoIncrementKeywordWorks() throws SQLException {
        // INTEGER PRIMARY KEY AUTOINCREMENT — explicit keyword, also rowid-alias.
        String sql = """
                CREATE TABLE notes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL
                );
                """;
        SchemaModel schema = new SqlFileIntrospector(sql, "sqlite").introspect("main");
        ColumnModel id = schema.findTable("notes").findColumn("id");
        assertThat(id.autoIncrement()).isTrue();
    }

    // ============================================================ helpers

    private static List<GeneratedUnit> generate(String dbDriver, String persistence,
                                                 boolean docker, boolean tests) {
        GeneratorContext ctx = ctx(dbDriver, persistence, docker, tests);
        return new MvcGenerator(new TemplateEngine(null), ctx).generate(schema());
    }

    private static GeneratorContext ctx(String dbDriver, String persistence,
                                         boolean docker, boolean tests) {
        return new GeneratorContext(
                "com.example.app", "app", "com.example",
                "3.3.5", "17", true,
                "mvc", persistence, "xml", false, "none", "constructor",
                "jakarta", "class", "separate", "problemdetail",
                UmabootConfig.AuditOptions.defaults(),
                UmabootConfig.SoftDeleteOptions.defaults(),
                docker ? new UmabootConfig.DockerOptions(true, "eclipse-temurin:17-jre-alpine", 8080)
                       : UmabootConfig.DockerOptions.defaults(),
                UmabootConfig.CiOptions.defaults(),
                UmabootConfig.LoggingOptions.defaults(),
                tests ? new UmabootConfig.TestOptions(true)
                      : UmabootConfig.TestOptions.defaults(),
                "offset",
                UmabootConfig.SecurityOptions.defaults(),
                UmabootConfig.DddOptions.defaults(),
                false, dbDriver, null, null, "", null);
    }

    private static SchemaModel schema() {
        ColumnModel id = new ColumnModel("id", Types.BIGINT, "integer", 19, 0,
                false, true, true, null, "", List.of());
        ColumnModel name = new ColumnModel("name", Types.VARCHAR, "text", 0, 0,
                false, false, false, null, "", List.of());
        TableModel users = new TableModel("users", "main", "",
                List.of(id, name),
                List.of("id"),
                List.of(),
                List.of(),
                false);
        return new SchemaModel("main", List.of(users));
    }

    private static String readUnit(List<GeneratedUnit> units, String path) {
        return units.stream()
                .filter(u -> u.relativePath().equals(path))
                .findFirst()
                .map(GeneratedUnit::content)
                .orElseThrow(() -> new AssertionError("Missing: " + path));
    }
}

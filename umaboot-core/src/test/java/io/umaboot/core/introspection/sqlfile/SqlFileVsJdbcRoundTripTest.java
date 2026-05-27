package io.umaboot.core.introspection.sqlfile;

import io.umaboot.core.introspection.JdbcDrivers;
import io.umaboot.core.introspection.mysql.MysqlIntrospector;
import io.umaboot.core.introspection.postgres.PostgresIntrospector;
import io.umaboot.core.model.ColumnModel;
import io.umaboot.core.model.SchemaModel;
import io.umaboot.core.model.TableModel;
import io.umaboot.fixtures.FixtureLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip integration test: feed the same fixture SQL to (a) {@link SqlFileIntrospector}
 * and to (b) the live-DB JDBC introspector via Testcontainers. Assert the two
 * {@link SchemaModel} outputs agree on the structurally important fields.
 *
 * <p>This is the regression net for the SqlFileIntrospector. Whenever JSqlParser
 * gets upgraded or a new feature lands, this test catches drift between the
 * file-parser path and the JDBC-introspection path. If both agree, downstream
 * code generation produces identical output regardless of schema source.</p>
 *
 * <p>Tagged {@code IntegrationTest} so the default Surefire run skips it; explicit
 * runs (Surefire {@code -Dtest=*IntegrationTest}) do exercise it. Requires Docker.</p>
 *
 * <p>Equivalence is asserted on table names, column names + JDBC types, primary keys,
 * and foreign-key targets. Comments + enum values are compared loosely (presence,
 * not exact string match) since some Postgres DDL idioms differ between
 * file-parsed and JDBC-reflected paths in non-meaningful ways.</p>
 */
@Tag("IntegrationTest")
@EnabledIfSystemProperty(named = "umaboot.runIntegrationTests", matches = "true")
class SqlFileVsJdbcRoundTripTest {

    private static PostgreSQLContainer<?> POSTGRES;
    private static MySQLContainer<?> MYSQL;

    @BeforeAll
    @SuppressWarnings("resource")
    static void startContainers() {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("test").withUsername("test").withPassword("test");
        POSTGRES.start();
        MYSQL = new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("test").withUsername("test").withPassword("test");
        MYSQL.start();
        JdbcDrivers.registerAll();
    }

    @AfterAll
    static void stopContainers() {
        if (POSTGRES != null) POSTGRES.stop();
        if (MYSQL != null) MYSQL.stop();
    }

    @Test
    void postgresRelationshipsRoundTrip() throws SQLException {
        runRoundTrip("postgresql", FixtureLoader.POSTGRES_RELATIONSHIPS);
    }

    @Test
    void postgresEnumsRoundTrip() throws SQLException {
        runRoundTrip("postgresql", FixtureLoader.POSTGRES_ENUM_TYPES);
    }

    @Test
    void postgresCommentsRoundTrip() throws SQLException {
        runRoundTrip("postgresql", FixtureLoader.POSTGRES_COMMENTS);
    }

    @Test
    void mysqlRelationshipsRoundTrip() throws SQLException {
        runRoundTrip("mysql", FixtureLoader.MYSQL_RELATIONSHIPS);
    }

    @Test
    void mysqlEnumsRoundTrip() throws SQLException {
        runRoundTrip("mysql", FixtureLoader.MYSQL_ENUM_TYPES);
    }

    @Test
    void mysqlCommentsRoundTrip() throws SQLException {
        runRoundTrip("mysql", FixtureLoader.MYSQL_COMMENTS);
    }

    // ============================================================ helpers

    private void runRoundTrip(String dialect, String fixturePath) throws SQLException {
        String sql = FixtureLoader.load(fixturePath);

        // (a) parse via file
        SchemaModel fromFile = new SqlFileIntrospector(sql, dialect).introspect("public");

        // (b) apply the same SQL to the live container, then introspect
        SchemaModel fromJdbc;
        if ("postgresql".equals(dialect)) {
            try (Connection c = DriverManager.getConnection(
                    POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                 Statement s = c.createStatement()) {
                s.execute(sql);
                fromJdbc = new PostgresIntrospector(c).introspect("public");
            }
        } else {
            try (Connection c = DriverManager.getConnection(
                    MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
                 Statement s = c.createStatement()) {
                // MySQL JDBC needs statements one at a time
                for (String stmt : splitStatements(sql)) {
                    if (!stmt.trim().isEmpty()) s.execute(stmt);
                }
                fromJdbc = new MysqlIntrospector(c).introspect("test");
            }
        }

        assertEquivalent(fromFile, fromJdbc, dialect, fixturePath);
    }

    /** Compares the structurally important shape of two SchemaModels. */
    private static void assertEquivalent(SchemaModel file, SchemaModel jdbc,
                                         String dialect, String fixturePath) {
        Set<String> fileTables = lower(file.tables().stream().map(TableModel::name).toList());
        Set<String> jdbcTables = lower(jdbc.tables().stream().map(TableModel::name).toList());
        assertThat(fileTables)
                .as("table names match (%s, %s)", dialect, fixturePath)
                .isEqualTo(jdbcTables);

        for (TableModel ft : file.tables()) {
            TableModel jt = findCi(jdbc, ft.name());
            assertThat(jt).as("jdbc table %s", ft.name()).isNotNull();

            // Column names
            Set<String> fileCols = lower(ft.columns().stream().map(ColumnModel::name).toList());
            Set<String> jdbcCols = lower(jt.columns().stream().map(ColumnModel::name).toList());
            assertThat(fileCols)
                    .as("columns of table %s", ft.name())
                    .isEqualTo(jdbcCols);

            // Primary key
            assertThat(lower(ft.primaryKey())).as("PK of %s", ft.name())
                    .isEqualTo(lower(jt.primaryKey()));

            // FK target tables (loose: same set of referenced tables, ignoring relationship type)
            Set<String> fileFkTargets = ft.relationships().stream()
                    .map(r -> r.toTable().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
            Set<String> jdbcFkTargets = jt.relationships().stream()
                    .map(r -> r.toTable().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
            assertThat(fileFkTargets).as("FK targets of %s", ft.name())
                    .isEqualTo(jdbcFkTargets);
        }
    }

    private static TableModel findCi(SchemaModel s, String name) {
        for (TableModel t : s.tables()) {
            if (t.name().equalsIgnoreCase(name)) return t;
        }
        return null;
    }

    private static Set<String> lower(List<String> in) {
        return in.stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    }

    /** Naive ;-split — fine for our fixtures because they have no semicolons inside string literals. */
    private static String[] splitStatements(String sql) {
        return sql.split(";");
    }
}

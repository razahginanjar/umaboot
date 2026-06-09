package io.umaboot.fixtures;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * Loads SQL fixture scripts from the {@code umaboot-test-fixtures} resources.
 *
 * <p>Two families of fixtures are bundled:</p>
 * <ul>
 *   <li>{@code POSTGRES_*} / {@code MYSQL_*} — focused per-condition scripts under
 *       {@code /fixtures/postgres/} and {@code /fixtures/mysql/} respectively.
 *       Each script isolates one generator-relevant condition (basic CRUD,
 *       relationships, audit columns, soft delete, composite PK, ENUMs, wide
 *       types, constraints, comments, naming edge cases). All are re-runnable —
 *       they {@code DROP IF EXISTS} their tables before recreating.</li>
 *   <li>{@link #POSTGRES_SAMPLE} — the original kitchen-sink schema used by
 *       {@code GenerateIntegrationTest}. Kept for backwards compatibility.</li>
 * </ul>
 *
 * <p>Apply a script against a live database from a JUnit test:</p>
 * <pre>{@code
 * String sql = FixtureLoader.load(FixtureLoader.POSTGRES_RELATIONSHIPS);
 * try (Statement st = conn.createStatement()) { st.execute(sql); }
 * }</pre>
 */
public final class FixtureLoader {

    /** Kitchen-sink schemas for broad script-mode parser/generation coverage. */
    public static final String POSTGRES_SAMPLE = "/fixtures/postgres/sample-schema.sql";
    public static final String MYSQL_SAMPLE = "/fixtures/mysql/sample-schema.sql";
    public static final String MARIADB_SAMPLE = "/fixtures/mariadb/sample-schema.sql";
    public static final String SQLITE_SAMPLE = "/fixtures/sqlite/sample-schema.sql";

    // ----- Postgres per-condition fixtures -----
    public static final String POSTGRES_BASIC_CRUD         = "/fixtures/postgres/01-basic-crud.sql";
    public static final String POSTGRES_RELATIONSHIPS      = "/fixtures/postgres/02-relationships.sql";
    public static final String POSTGRES_AUDIT_COLUMNS      = "/fixtures/postgres/03-audit-columns.sql";
    public static final String POSTGRES_SOFT_DELETE        = "/fixtures/postgres/04-soft-delete.sql";
    public static final String POSTGRES_COMPOSITE_PK       = "/fixtures/postgres/05-composite-pk.sql";
    public static final String POSTGRES_ENUM_TYPES         = "/fixtures/postgres/06-enum-types.sql";
    public static final String POSTGRES_WIDE_TYPES         = "/fixtures/postgres/07-wide-types.sql";
    public static final String POSTGRES_CONSTRAINTS        = "/fixtures/postgres/08-constraints.sql";
    public static final String POSTGRES_COMMENTS           = "/fixtures/postgres/09-comments.sql";
    public static final String POSTGRES_NAMING_EDGE_CASES  = "/fixtures/postgres/10-naming-edge-cases.sql";

    // ----- MySQL per-condition fixtures -----
    public static final String MYSQL_BASIC_CRUD            = "/fixtures/mysql/01-basic-crud.sql";
    public static final String MYSQL_RELATIONSHIPS         = "/fixtures/mysql/02-relationships.sql";
    public static final String MYSQL_AUDIT_COLUMNS         = "/fixtures/mysql/03-audit-columns.sql";
    public static final String MYSQL_SOFT_DELETE           = "/fixtures/mysql/04-soft-delete.sql";
    public static final String MYSQL_COMPOSITE_PK          = "/fixtures/mysql/05-composite-pk.sql";
    public static final String MYSQL_ENUM_TYPES            = "/fixtures/mysql/06-enum-types.sql";
    public static final String MYSQL_WIDE_TYPES            = "/fixtures/mysql/07-wide-types.sql";
    public static final String MYSQL_CONSTRAINTS           = "/fixtures/mysql/08-constraints.sql";
    public static final String MYSQL_COMMENTS              = "/fixtures/mysql/09-comments.sql";
    public static final String MYSQL_NAMING_EDGE_CASES     = "/fixtures/mysql/10-naming-edge-cases.sql";

    /** All Postgres per-condition scripts in numeric order. */
    public static final List<String> POSTGRES_SCENARIOS = List.of(
            POSTGRES_SAMPLE,
            POSTGRES_BASIC_CRUD,
            POSTGRES_RELATIONSHIPS,
            POSTGRES_AUDIT_COLUMNS,
            POSTGRES_SOFT_DELETE,
            POSTGRES_COMPOSITE_PK,
            POSTGRES_ENUM_TYPES,
            POSTGRES_WIDE_TYPES,
            POSTGRES_CONSTRAINTS,
            POSTGRES_COMMENTS,
            POSTGRES_NAMING_EDGE_CASES
    );

    /** All MySQL per-condition scripts in numeric order. */
    public static final List<String> MYSQL_SCENARIOS = List.of(
            MYSQL_SAMPLE,
            MYSQL_BASIC_CRUD,
            MYSQL_RELATIONSHIPS,
            MYSQL_AUDIT_COLUMNS,
            MYSQL_SOFT_DELETE,
            MYSQL_COMPOSITE_PK,
            MYSQL_ENUM_TYPES,
            MYSQL_WIDE_TYPES,
            MYSQL_CONSTRAINTS,
            MYSQL_COMMENTS,
            MYSQL_NAMING_EDGE_CASES
    );

    /** All MariaDB scripts in numeric order. */
    public static final List<String> MARIADB_SCENARIOS = List.of(
            MARIADB_SAMPLE
    );

    /** All SQLite scripts in numeric order. */
    public static final List<String> SQLITE_SCENARIOS = List.of(
            SQLITE_SAMPLE
    );

    private FixtureLoader() {}

    /**
     * Read a fixture's SQL contents as a UTF-8 string.
     *
     * @param resourcePath classpath path, e.g. {@link #POSTGRES_RELATIONSHIPS}
     * @return the SQL script body
     * @throws IllegalArgumentException if the resource is not on the classpath
     */
    public static String load(String resourcePath) {
        Objects.requireNonNull(resourcePath, "resourcePath");
        try (InputStream in = FixtureLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) throw new IllegalArgumentException("Fixture not found: " + resourcePath);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + resourcePath, e);
        }
    }
}

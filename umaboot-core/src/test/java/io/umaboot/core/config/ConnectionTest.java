package io.umaboot.core.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link UmabootConfig.Connection} record contract — the
 * canonical-form validation, the URL composition / parsing helpers, and the
 * engine-aware {@code introspectionTarget()} dispatch.
 *
 * <p>These tests exercise the record directly without going through YAML so
 * they're fast and isolated from the loader's own concerns. The YAML
 * round-trip path is covered by {@link UmabootConfigLoaderTest}.</p>
 */
class ConnectionTest {

    // ---------------------------------------------------------------- introspectionTarget

    @Test
    void introspectionTarget_postgres_isSchema() {
        var conn = new UmabootConfig.Connection(
                "host", "postgresql",
                "localhost:5432", "", null,
                "shop", "public",
                "u", "p", null);
        assertThat(conn.introspectionTarget()).isEqualTo("public");
    }

    @Test
    void introspectionTarget_mysql_isDatabase_whenDatabaseSet() {
        var conn = new UmabootConfig.Connection(
                "host", "mysql",
                "localhost:3306", "", null,
                "orders", "",
                "u", "p", null);
        assertThat(conn.introspectionTarget()).isEqualTo("orders");
    }

    @Test
    void introspectionTarget_mysql_fallsBackToSchema_whenDatabaseEmpty() {
        // url-mode bypasses the strict "database required" check that host-mode
        // applies, so we can construct the legacy-fallback case (URL without DB
        // path + empty database + DB name in the schema field).
        var conn = new UmabootConfig.Connection(
                "url", "mysql",
                null, null,
                "jdbc:mysql://localhost:3306",   // url with no DB path
                "",                              // empty database
                "legacy_db",                     // legacy: DB name lives in schema
                "u", "p", null);
        assertThat(conn.database()).isEqualTo("");
        assertThat(conn.introspectionTarget()).isEqualTo("legacy_db");
    }

    @Test
    void introspectionTarget_postgres_returnsEmptyWhenSchemaEmpty() {
        // url-mode lets us construct an "incomplete" config that level-1 fail-fast
        // is supposed to catch downstream.
        var conn = new UmabootConfig.Connection(
                "url", "postgresql",
                null, null,
                "jdbc:postgresql://localhost:5432/shop",
                "shop",
                "",                              // empty schema
                "u", "p", null);
        assertThat(conn.introspectionTarget()).isEqualTo("");
    }

    // ---------------------------------------------------------------- canonical-form validation

    @Test
    void hostMode_emptyDatabase_throws() {
        assertThatThrownBy(() -> new UmabootConfig.Connection(
                "host", "postgresql",
                "localhost:5432", "", null,
                "",            // empty database
                "public",
                "u", "p", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("database is required");
    }

    @Test
    void hostMode_emptyHost_throws() {
        assertThatThrownBy(() -> new UmabootConfig.Connection(
                "host", "postgresql",
                "",            // empty host
                "", null,
                "shop", "public",
                "u", "p", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("host is required");
    }

    @Test
    void hostMode_paramsWithLeadingQuestionMark_throws() {
        assertThatThrownBy(() -> new UmabootConfig.Connection(
                "host", "postgresql",
                "localhost:5432", "?ssl=true", null,
                "shop", "public",
                "u", "p", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not start with '?'");
    }

    @Test
    void urlMode_emptyUrl_throws() {
        assertThatThrownBy(() -> new UmabootConfig.Connection(
                "url", "postgresql",
                null, null,
                "",            // empty url
                "shop", "public",
                "u", "p", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("url is required");
    }

    @Test
    void invalidMode_throws() {
        assertThatThrownBy(() -> new UmabootConfig.Connection(
                "WAT", "postgresql",
                "localhost:5432", "", null,
                "shop", "public",
                "u", "p", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mode must be 'host' or 'url'");
    }

    @Test
    void legacyDriverPostgresAlias_normalizedToPostgresql() {
        var conn = new UmabootConfig.Connection(
                "url", "postgres",   // legacy alias
                null, null,
                "jdbc:postgresql://localhost:5432/shop",
                "shop", "public",
                "u", "p", null);
        assertThat(conn.type()).isEqualTo("postgresql");
        assertThat(conn.driver()).isEqualTo("postgresql");
    }

    @Test
    void mode_canonicalizesToLowercaseAndTrim() {
        var conn = new UmabootConfig.Connection(
                "HOST", "postgresql",
                "localhost:5432", "", null,
                "shop", "public",
                "u", "p", null);
        assertThat(conn.mode()).isEqualTo("host");
    }

    // ---------------------------------------------------------------- composeUrl

    @Test
    void composeUrl_withParams_singleQuestionMark() {
        String url = UmabootConfig.Connection.composeUrl(
                "postgresql", "localhost:5432", "shop", "useSSL=false");
        assertThat(url).isEqualTo("jdbc:postgresql://localhost:5432/shop?useSSL=false");
    }

    @Test
    void composeUrl_emptyParams_noQuestionMark() {
        String url = UmabootConfig.Connection.composeUrl(
                "mysql", "localhost:3306", "orders", "");
        assertThat(url).isEqualTo("jdbc:mysql://localhost:3306/orders");
    }

    @Test
    void composeUrl_nullParams_noQuestionMark() {
        String url = UmabootConfig.Connection.composeUrl(
                "mysql", "localhost:3306", "orders", null);
        assertThat(url).isEqualTo("jdbc:mysql://localhost:3306/orders");
    }

    // ---------------------------------------------------------------- parseDatabaseFromUrl

    @Test
    void parseDatabaseFromUrl_simple() {
        assertThat(UmabootConfig.Connection.parseDatabaseFromUrl(
                "jdbc:mysql://localhost:3306/orders"))
                .isEqualTo("orders");
    }

    @Test
    void parseDatabaseFromUrl_withParams() {
        assertThat(UmabootConfig.Connection.parseDatabaseFromUrl(
                "jdbc:mysql://localhost:3306/orders?useSSL=false&applicationName=umaboot"))
                .isEqualTo("orders");
    }

    @Test
    void parseDatabaseFromUrl_noPath_returnsEmpty() {
        assertThat(UmabootConfig.Connection.parseDatabaseFromUrl(
                "jdbc:mysql://localhost:3306"))
                .isEqualTo("");
    }

    @Test
    void parseDatabaseFromUrl_trailingSlash_returnsEmpty() {
        assertThat(UmabootConfig.Connection.parseDatabaseFromUrl(
                "jdbc:mysql://localhost:3306/"))
                .isEqualTo("");
    }

    @Test
    void parseDatabaseFromUrl_garbageInput_returnsEmpty() {
        assertThat(UmabootConfig.Connection.parseDatabaseFromUrl(null)).isEqualTo("");
        assertThat(UmabootConfig.Connection.parseDatabaseFromUrl("not-a-url")).isEqualTo("");
    }
}

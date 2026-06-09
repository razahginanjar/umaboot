package io.umaboot.core.introspection;

import io.umaboot.core.config.UmabootConfig;
import io.umaboot.core.config.UmabootConfigLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaIntrospectionServiceTest {

    @Test
    void sqliteLiveConnectionAllowsEmptyIntrospectionTarget(@TempDir Path tempDir) throws Exception {
        Path yaml = tempDir.resolve("umaboot.yaml");
        Files.writeString(yaml, """
                connection:
                  mode: host
                  type: sqlite
                  database: ":memory:"
                generation:
                  basePackage: com.example.sqlite
                  projectName: sqlite-demo
                """);

        UmabootConfig config = UmabootConfigLoader.load(yaml);
        SchemaIntrospectionService.Result result = new SchemaIntrospectionService().introspect(config);

        assertThat(result.dbType()).isEqualTo("sqlite");
        assertThat(result.schema().schemaName()).isEqualTo("main");
        assertThat(result.schema().tables()).isEmpty();
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void livePostgresEmptySchemaWarningNamesSchemaTarget() {
        UmabootConfig.Connection connection = new UmabootConfig.Connection(
                "host", "postgresql", "localhost:5432", "", "",
                "demo", "public", "user", "pass", null);

        var warnings = SchemaIntrospectionService.emptyLiveSchemaWarnings(connection, "public");

        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0))
                .contains("schema 'public'")
                .contains("Database field selects the database")
                .contains("Schema field selects where tables are read from");
    }

    @Test
    void liveMysqlEmptySchemaWarningNamesDatabaseTarget() {
        UmabootConfig.Connection connection = new UmabootConfig.Connection(
                "host", "mysql", "localhost:3306", "", "",
                "inventory", "public", "user", "pass", null);

        var warnings = SchemaIntrospectionService.emptyLiveSchemaWarnings(connection, "inventory");

        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0))
                .contains("database 'inventory'")
                .contains("Database field is the table-introspection target")
                .contains("connection.database");
    }

    @Test
    void sqliteEmptySchemaWarningIsSuppressed() {
        UmabootConfig.Connection connection = new UmabootConfig.Connection(
                "host", "sqlite", "", "", "",
                ":memory:", "main", "", "", null);

        assertThat(SchemaIntrospectionService.emptyLiveSchemaWarnings(connection, "")).isEmpty();
    }
}

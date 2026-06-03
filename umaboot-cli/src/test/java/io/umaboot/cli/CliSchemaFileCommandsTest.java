package io.umaboot.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CliSchemaFileCommandsTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void listTablesReadsSchemaFileAndHidesJunctionTablesByDefault() throws Exception {
        Path config = writeSchemaFileConfig();

        CapturedCommand result = execute("list-tables", "--config", config.toString());

        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).contains("users", "roles");
        assertThat(result.stdout()).doesNotContain("user_roles");
        assertThat(result.stderr()).isEmpty();
    }

    @Test
    void listTablesCanIncludeJunctionTables() throws Exception {
        Path config = writeSchemaFileConfig();

        CapturedCommand result = execute("list-tables", "--config", config.toString(), "--all");

        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).contains("users", "roles", "user_roles");
        assertThat(result.stderr()).isEmpty();
    }

    @Test
    void listTablesRawAllKeepsScriptParserTablesBeforeRelationshipAnalysis() throws Exception {
        Path config = writeSchemaFileConfig();

        CapturedCommand result = execute("list-tables", "--config", config.toString(), "--raw", "--all");

        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).contains("users", "roles", "user_roles");
        assertThat(result.stderr()).isEmpty();
    }

    @Test
    void listTablesRawAllFallsBackToCreateTableNamesForSqlServerBatches() throws Exception {
        Path config = writeSchemaFileConfig("""
                SET ANSI_NULLS ON
                GO
                CREATE TABLE [dbo].[Customers] (
                  [id] [bigint] IDENTITY(1,1) NOT NULL,
                  [name] [varchar](100) NOT NULL,
                  CONSTRAINT [PK_Customers] PRIMARY KEY CLUSTERED ([id] ASC)
                )
                GO

                CREATE TABLE [dbo].[Orders] (
                  [id] [bigint] IDENTITY(1,1) NOT NULL,
                  [customer_id] [bigint] NOT NULL
                )
                GO
                """, "sqlserver");

        CapturedCommand result = execute("list-tables", "--config", config.toString(), "--raw", "--all");

        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).contains("Customers", "Orders");
    }

    @Test
    void describeSchemaReturnsColumnMetadataAndOverrideTypes() throws Exception {
        Path config = writeSchemaFileConfig();

        CapturedCommand result = execute("describe-schema", "--config", config.toString());

        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stderr()).isEmpty();

        JsonNode root = JSON.readTree(result.stdout());
        assertThat(root.path("dbType").asText()).isEqualTo("postgresql");
        assertThat(root.path("javaTypes")).anyMatch(n -> "java.util.UUID".equals(n.asText()));

        JsonNode tables = root.path("tables");
        assertThat(tables).hasSize(2);
        JsonNode users = table(tables, "users");
        assertThat(users.path("defaultClassName").asText()).isEqualTo("User");
        assertThat(column(users, "id").path("defaultJavaType").asText()).isEqualTo("Long");
        assertThat(column(users, "name").path("sqlType").asText()).containsIgnoringCase("varchar");
    }

    @Test
    void testConnectionSkipsSchemaFileMode() throws Exception {
        Path config = writeSchemaFileConfig();

        CapturedCommand result = execute("test-connection", "--config", config.toString());

        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).contains("SKIP schemaFile mode");
        assertThat(result.stderr()).isEmpty();
    }

    private Path writeSchemaFileConfig() throws Exception {
        Path schema = tempDir.resolve("schema.sql");
        return writeSchemaFileConfig("""
                CREATE TABLE users (
                  id BIGINT PRIMARY KEY,
                  name VARCHAR(100)
                );

                CREATE TABLE roles (
                  id BIGINT PRIMARY KEY,
                  name VARCHAR(100)
                );

                CREATE TABLE user_roles (
                  user_id BIGINT NOT NULL,
                  role_id BIGINT NOT NULL,
                  PRIMARY KEY (user_id, role_id),
                  FOREIGN KEY (user_id) REFERENCES users(id),
                  FOREIGN KEY (role_id) REFERENCES roles(id)
                );
                """, "postgresql");
    }

    private Path writeSchemaFileConfig(String sql, String dialect) throws Exception {
        Path schema = tempDir.resolve("schema-" + dialect + ".sql");
        Files.writeString(schema, sql);
        Path config = tempDir.resolve("umaboot.yaml");
        Files.writeString(config, """
                schemaFile: "%s"
                schemaDialect: %s
                generation:
                  architecture: mvc
                  persistence: jpa
                  basePackage: com.example.demo
                  projectName: demo
                """.formatted(schema.toString().replace('\\', '/'), dialect));
        return config;
    }

    private static JsonNode table(JsonNode tables, String name) {
        for (JsonNode table : tables) {
            if (name.equals(table.path("name").asText())) return table;
        }
        throw new AssertionError("table not found: " + name);
    }

    private static JsonNode column(JsonNode table, String name) {
        for (JsonNode column : table.path("columns")) {
            if (name.equals(column.path("name").asText())) return column;
        }
        throw new AssertionError("column not found: " + name);
    }

    private static CapturedCommand execute(String... args) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
            int exitCode = new CommandLine(new UmabootCli()).execute(args);
            return new CapturedCommand(
                    exitCode,
                    out.toString(StandardCharsets.UTF_8),
                    err.toString(StandardCharsets.UTF_8));
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    private record CapturedCommand(int exitCode, String stdout, String stderr) {}
}

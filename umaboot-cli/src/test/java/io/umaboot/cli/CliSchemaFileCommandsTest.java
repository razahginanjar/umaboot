package io.umaboot.cli;

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
    void testConnectionSkipsSchemaFileMode() throws Exception {
        Path config = writeSchemaFileConfig();

        CapturedCommand result = execute("test-connection", "--config", config.toString());

        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).contains("SKIP schemaFile mode");
        assertThat(result.stderr()).isEmpty();
    }

    private Path writeSchemaFileConfig() throws Exception {
        Path schema = tempDir.resolve("schema.sql");
        Files.writeString(schema, """
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
                """);

        Path config = tempDir.resolve("umaboot.yaml");
        Files.writeString(config, """
                schemaFile: "%s"
                schemaDialect: postgresql
                generation:
                  architecture: mvc
                  persistence: jpa
                  basePackage: com.example.demo
                  projectName: demo
                """.formatted(schema.toString().replace('\\', '/')));
        return config;
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

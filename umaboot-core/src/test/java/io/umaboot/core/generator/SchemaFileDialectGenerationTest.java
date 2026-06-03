package io.umaboot.core.generator;

import io.umaboot.core.GenerationPipeline;
import io.umaboot.core.config.UmabootConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaFileDialectGenerationTest {

    @Test
    void mysqlSchemaFileDialectDrivesGradleDockerCiAndTests(@TempDir Path tmp) throws Exception {
        GenerationPipeline.Result result = runFromMysqlSchema(tmp, "gradle");
        List<GeneratedUnit> units = result.units();

        assertThat(result.ctx().dbDriver()).isEqualTo("mysql");

        String app = readUnit(units, "src/main/resources/application.yml");
        assertThat(app)
                .contains("driver-class-name: com.mysql.cj.jdbc.Driver")
                .contains("jdbc:mysql://localhost:3306/orders-api")
                .doesNotContain("org.postgresql.Driver")
                .doesNotContain("jdbc:postgresql:");

        String gradle = readUnit(units, "build.gradle.kts");
        assertThat(gradle)
                .contains("runtimeOnly(\"com.mysql:mysql-connector-j\")")
                .contains("testImplementation(platform(\"org.testcontainers:testcontainers-bom:$testcontainersVersion\"))")
                .contains("testImplementation(\"org.testcontainers:mysql\")")
                .doesNotContain("runtimeOnly(\"org.postgresql:postgresql\")")
                .doesNotContain("testImplementation(\"org.testcontainers:postgresql\")");

        String compose = readUnit(units, "docker-compose.yml");
        assertThat(compose)
                .contains("image: mysql:8.0")
                .contains("jdbc:mysql://db:3306")
                .doesNotContain("postgres:16");

        String baseTest = readUnit(units, "src/test/java/com/example/orders/AbstractIntegrationTest.java");
        assertThat(baseTest)
                .contains("import org.testcontainers.containers.MySQLContainer;")
                .contains("new MySQLContainer<>(\"mysql:8.0\")")
                .doesNotContain("PostgreSQLContainer");

        String ci = readUnit(units, ".github/workflows/ci.yml");
        assertThat(ci)
                .contains("gradle --no-daemon compileJava")
                .contains("gradle --no-daemon test")
                .doesNotContain("postgresql");
    }

    @Test
    void mysqlSchemaFileMavenUsesTestcontainersBom(@TempDir Path tmp) throws Exception {
        List<GeneratedUnit> units = runFromMysqlSchema(tmp, "maven").units();
        String pom = readUnit(units, "pom.xml");

        assertThat(pom)
                .contains("<testcontainers.version>1.20.4</testcontainers.version>")
                .contains("<artifactId>testcontainers-bom</artifactId>")
                .contains("<scope>import</scope>")
                .contains("<artifactId>junit-jupiter</artifactId>")
                .contains("<artifactId>mysql</artifactId>")
                .doesNotContain("<artifactId>postgresql</artifactId>")
                .doesNotContain("<version>1.20.4</version>");
    }

    private static GenerationPipeline.Result runFromMysqlSchema(Path tmp, String buildTool) throws Exception {
        Path schema = tmp.resolve("schema.sql");
        Files.writeString(schema, """
                CREATE TABLE users (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  username VARCHAR(100) NOT NULL
                );
                """);

        var generation = new UmabootConfig.Generation(
                "mvc",
                "jpa",
                "com.example.orders",
                "orders-api",
                "com.example",
                "3.3.5",
                "17",
                true,
                UmabootConfig.OpenApiOptions.defaults(),
                UmabootConfig.InjectionOptions.defaults(),
                UmabootConfig.ValidationOptions.defaults(),
                UmabootConfig.DtoOptions.defaults(),
                UmabootConfig.ExceptionOptions.defaults(),
                UmabootConfig.AuditOptions.defaults(),
                UmabootConfig.SoftDeleteOptions.defaults(),
                new UmabootConfig.DockerOptions(true, "eclipse-temurin:17-jre-alpine", 8080),
                new UmabootConfig.CiOptions("github"),
                UmabootConfig.LoggingOptions.defaults(),
                new UmabootConfig.TestOptions(true),
                UmabootConfig.MigrationOptions.defaults(),
                UmabootConfig.PaginationOptions.defaults(),
                UmabootConfig.SecurityOptions.defaults(),
                "./generated",
                new UmabootConfig.JpaOptions(false),
                new UmabootConfig.MyBatisOptions("xml"),
                UmabootConfig.TableFilterOptions.allowAll(),
                UmabootConfig.DddOptions.defaults(),
                UmabootConfig.OutputOptions.defaults(),
                UmabootConfig.ApplicationConfigOptions.defaults(),
                schema.toString(),
                "mysql",
                buildTool);
        return GenerationPipeline.run(new UmabootConfig(null, generation), null);
    }

    private static String readUnit(List<GeneratedUnit> units, String path) {
        return units.stream()
                .filter(u -> u.relativePath().equals(path))
                .findFirst()
                .map(GeneratedUnit::content)
                .orElseThrow(() -> new AssertionError("Missing: " + path));
    }
}

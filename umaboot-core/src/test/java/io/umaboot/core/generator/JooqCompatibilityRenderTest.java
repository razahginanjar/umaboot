package io.umaboot.core.generator;

import io.umaboot.core.config.UmabootConfig;
import io.umaboot.core.generator.ddd.DddGenerator;
import io.umaboot.core.generator.hexagonal.HexagonalGenerator;
import io.umaboot.core.generator.mvc.MvcGenerator;
import io.umaboot.core.model.ColumnModel;
import io.umaboot.core.model.SchemaModel;
import io.umaboot.core.model.TableModel;
import io.umaboot.core.template.TemplateEngine;
import org.junit.jupiter.api.Test;

import java.sql.Types;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JooqCompatibilityRenderTest {

    @Test
    void springBoot2Java8Maven_usesJava8CompatibleJooqAndCodegenDrivers() {
        assertSpringBoot2MavenDriver("mysql", "com.mysql", "mysql-connector-j", "8.0.33");
        assertSpringBoot2MavenDriver("mariadb", "org.mariadb.jdbc", "mariadb-java-client", "3.1.4");
        assertSpringBoot2MavenDriver("sqlserver", "com.microsoft.sqlserver", "mssql-jdbc", "10.2.3.jre8");
        assertSpringBoot2MavenDriver("sqlite", "org.xerial", "sqlite-jdbc", "3.36.0.3");
        assertSpringBoot2MavenDriver("postgresql", "org.postgresql", "postgresql", "42.3.8");
    }

    @Test
    void springBoot2Java8Gradle_usesCompatibleJooqPluginAndDriver() {
        String kts = readUnit(generate("mvc", "gradle", "jooq", "sqlserver",
                "2.7.18", "1.8", false, "none"), "build.gradle.kts");

        assertThat(kts)
                .contains("id(\"nu.studer.jooq\") version \"5.2.2\"")
                .contains("extra[\"jooq.version\"] = \"3.14.16\"")
                .contains("version.set(\"3.14.16\")")
                .contains("jooqGenerator(\"com.microsoft.sqlserver:mssql-jdbc:10.2.3.jre8\")")
                .contains("JavaLanguageVersion.of(8)")
                .doesNotContain("3.16.23")
                .doesNotContain("12.6.4.jre11")
                .doesNotContain("version \"8.2\"");
    }

    @Test
    void springBoot2Java8GradleTooling_usesGradle7Runner() {
        List<GeneratedUnit> githubUnits = generate("mvc", "gradle", "jooq", "postgresql",
                "2.7.18", "1.8", false, "github");
        List<GeneratedUnit> gitlabUnits = generate("mvc", "gradle", "jooq", "postgresql",
                "2.7.18", "1.8", false, "gitlab");
        List<GeneratedUnit> dockerUnits = generate("mvc", "gradle", "jooq", "postgresql",
                "2.7.18", "1.8", true, "none");

        assertThat(readUnit(githubUnits, ".github/workflows/ci.yml"))
                .contains("gradle-version: 7.6.4")
                .doesNotContain("gradle-version: 8.11");
        assertThat(readUnit(gitlabUnits, ".gitlab-ci.yml"))
                .contains("image: gradle:7.6.4-jdk8")
                .doesNotContain("gradle:8.11");
        assertThat(readUnit(dockerUnits, "Dockerfile"))
                .contains("FROM gradle:7.6.4-jdk8 AS build")
                .doesNotContain("gradle:8.11");
    }

    @Test
    void springBoot3Jooq_keepsJava17CompatibleVersions() {
        String kts = readUnit(generate("hexagonal", "gradle", "jooq", "mysql",
                "3.3.5", "17", false, "none"), "build.gradle.kts");

        assertThat(kts)
                .contains("id(\"nu.studer.jooq\") version \"9.0\"")
                .contains("extra[\"jooq.version\"] = \"3.19.15\"")
                .contains("jooqGenerator(\"com.mysql:mysql-connector-j:8.4.0\")")
                .contains("version.set(\"3.19.15\")");
    }

    @Test
    void allArchitecturesReceiveJooqCompatibilityModelValues() {
        for (String architecture : List.of("mvc", "hexagonal", "ddd")) {
            String pom = readUnit(generate(architecture, "maven", "jooq", "mysql",
                    "2.7.18", "8", false, "none"), "pom.xml");

            assertThat(pom)
                    .contains("<jooq.version>3.14.16</jooq.version>")
                    .contains("<groupId>com.mysql</groupId>")
                    .contains("<artifactId>mysql-connector-j</artifactId>")
                    .contains("<version>8.0.33</version>")
                    .doesNotContain("<jooq.version>3.16.23</jooq.version>");
        }
    }

    private static void assertSpringBoot2MavenDriver(String dbDriver, String groupId,
                                                     String artifactId, String version) {
        String pom = readUnit(generate("mvc", "maven", "jooq", dbDriver,
                "2.7.18", "1.8", false, "none"), "pom.xml");

        assertThat(pom)
                .contains("<jooq.version>3.14.16</jooq.version>")
                .contains("<groupId>" + groupId + "</groupId>")
                .contains("<artifactId>" + artifactId + "</artifactId>")
                .contains("<version>" + version + "</version>")
                .doesNotContain("<jooq.version>3.16.23</jooq.version>");
    }

    private static List<GeneratedUnit> generate(String architecture, String buildTool,
                                                String persistence, String dbDriver,
                                                String springBootVersion, String javaVersion,
                                                boolean docker, String ciStyle) {
        GeneratorContext ctx = new GeneratorContext(
                "com.example.shop", "app", "com.example",
                springBootVersion, javaVersion, true,
                architecture, persistence, "xml", false, "none", "constructor",
                "jakarta", "class", "separate",
                springBootVersion.startsWith("2.") ? "envelope" : "problemdetail",
                UmabootConfig.AuditOptions.defaults(),
                UmabootConfig.SoftDeleteOptions.defaults(),
                docker ? new UmabootConfig.DockerOptions(true, "eclipse-temurin:17-jre-alpine", 8080)
                       : UmabootConfig.DockerOptions.defaults(),
                new UmabootConfig.CiOptions(ciStyle),
                UmabootConfig.LoggingOptions.defaults(),
                UmabootConfig.TestOptions.defaults(),
                "offset",
                UmabootConfig.SecurityOptions.defaults(),
                UmabootConfig.DddOptions.defaults(),
                false, dbDriver, null, null, "", null, buildTool);

        if ("hexagonal".equals(architecture)) {
            return new HexagonalGenerator(new TemplateEngine(null), ctx).generate(schema());
        }
        if ("ddd".equals(architecture)) {
            return new DddGenerator(new TemplateEngine(null), ctx).generate(schema());
        }
        return new MvcGenerator(new TemplateEngine(null), ctx).generate(schema());
    }

    private static SchemaModel schema() {
        ColumnModel id = new ColumnModel("id", Types.BIGINT, "bigint", 19, 0,
                false, true, true, null, "", List.of());
        ColumnModel name = new ColumnModel("name", Types.VARCHAR, "varchar", 100, 0,
                false, false, false, null, "", List.of());
        TableModel users = new TableModel("users", "public", "",
                List.of(id, name), List.of("id"), List.of(), List.of(), false);
        return new SchemaModel("public", List.of(users));
    }

    private static String readUnit(List<GeneratedUnit> units, String path) {
        return units.stream()
                .filter(unit -> unit.relativePath().equals(path))
                .findFirst()
                .map(GeneratedUnit::content)
                .orElseThrow(() -> new AssertionError("Missing: " + path));
    }
}

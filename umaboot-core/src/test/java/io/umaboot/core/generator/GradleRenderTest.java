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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates Gradle Kotlin DSL output and locks render-parity between Maven and
 * Gradle. The parity check extracts {@code groupId:artifactId} pairs from the
 * pom and asserts each one appears in the corresponding {@code build.gradle.kts},
 * so adding a dep to one without the other surfaces here.
 *
 * <p>Doesn't actually run Gradle — that's an integration concern (I-36 in
 * INTEGRATION_TESTING.md). These are pure render-shape tests.</p>
 */
class GradleRenderTest {

    // --------------------------------------------------------- emit shape

    @Test
    void mvc_gradle_emitsBuildGradleKtsAndSettings_notPom() {
        List<GeneratedUnit> units = generateMvc("gradle", "jpa");
        assertHasFile(units, "build.gradle.kts");
        assertHasFile(units, "settings.gradle.kts");
        assertNoFile(units, "pom.xml");
    }

    @Test
    void mvc_maven_emitsPom_notGradleFiles() {
        // Regression: switching default to maven still produces pom and skips gradle files.
        List<GeneratedUnit> units = generateMvc("maven", "jpa");
        assertHasFile(units, "pom.xml");
        assertNoFile(units, "build.gradle.kts");
        assertNoFile(units, "settings.gradle.kts");
    }

    @Test
    void hex_gradle_emitsBuildGradleKtsAndSettings() {
        List<GeneratedUnit> units = generateHex("gradle", "jpa");
        assertHasFile(units, "build.gradle.kts");
        assertHasFile(units, "settings.gradle.kts");
        assertNoFile(units, "pom.xml");
    }

    @Test
    void ddd_gradle_emitsBuildGradleKtsAndSettings() {
        List<GeneratedUnit> units = generateDdd("gradle", "jpa");
        assertHasFile(units, "build.gradle.kts");
        assertHasFile(units, "settings.gradle.kts");
        assertNoFile(units, "pom.xml");
    }

    // --------------------------------------------------------- gradle DSL shape

    @Test
    void gradle_pluginsBlockHasSpringBootAndDependencyManagement() {
        String kts = readUnit(generateMvc("gradle", "jpa"), "build.gradle.kts");
        assertThat(kts).contains("id(\"org.springframework.boot\")");
        assertThat(kts).contains("id(\"io.spring.dependency-management\")");
        assertThat(kts).contains("java {");
        assertThat(kts).contains("JavaLanguageVersion.of(17)");
    }

    @Test
    void gradle_jpaUsesSpringBootStarterDataJpa() {
        String kts = readUnit(generateMvc("gradle", "jpa"), "build.gradle.kts");
        assertThat(kts).contains("implementation(\"org.springframework.boot:spring-boot-starter-data-jpa\")");
    }

    @Test
    void gradle_mybatisUsesStarterAndDeclaresVersionVar() {
        String kts = readUnit(generateMvc("gradle", "mybatis"), "build.gradle.kts");
        assertThat(kts).contains("val mybatisStarterVersion =");
        assertThat(kts).contains("mybatis-spring-boot-starter:");
        // Non-JPA path must declare spring-data-commons explicitly so PageResponse compiles.
        assertThat(kts).contains("org.springframework.data:spring-data-commons");
    }

    @Test
    void gradle_jooqPluginAndConfiguration() {
        String kts = readUnit(generateMvc("gradle", "jooq"), "build.gradle.kts");
        assertThat(kts).contains("id(\"nu.studer.jooq\")");
        assertThat(kts).contains("jooq {");
        assertThat(kts).contains("nu.studer.gradle.jooq.JooqEdition.OSS");
        assertThat(kts).contains("PostgresDatabase");
        assertThat(kts).contains("packageName = \"com.example.shop.jooq\"");
        // Codegen needs the JDBC driver on its plugin classpath.
        assertThat(kts).contains("jooqGenerator(");
    }

    @Test
    void gradle_dbDriverBranchesCorrectly() {
        assertThat(readUnit(generateMvc("gradle", "jpa", "mariadb"), "build.gradle.kts"))
                .contains("runtimeOnly(\"org.mariadb.jdbc:mariadb-java-client\")");
        assertThat(readUnit(generateMvc("gradle", "jpa", "mysql"), "build.gradle.kts"))
                .contains("runtimeOnly(\"com.mysql:mysql-connector-j\")");
        assertThat(readUnit(generateMvc("gradle", "jpa", "sqlserver"), "build.gradle.kts"))
                .contains("runtimeOnly(\"com.microsoft.sqlserver:mssql-jdbc\")");
        assertThat(readUnit(generateMvc("gradle", "jpa", "sqlite"), "build.gradle.kts"))
                .contains("runtimeOnly(\"org.xerial:sqlite-jdbc\")");
        assertThat(readUnit(generateMvc("gradle", "jpa", "postgresql"), "build.gradle.kts"))
                .contains("runtimeOnly(\"org.postgresql:postgresql\")");
    }

    @Test
    void gradle_sqliteJpaPullsCommunityDialects() {
        String kts = readUnit(generateMvc("gradle", "jpa", "sqlite"), "build.gradle.kts");
        assertThat(kts).contains("hibernate-community-dialects");
    }

    @Test
    void gradle_sqliteSkipsTestcontainers() {
        String kts = readUnit(generateMvcWithTests("gradle", "jpa", "sqlite", true), "build.gradle.kts");
        assertThat(kts).doesNotContain("org.testcontainers:");
    }

    @Test
    void gradle_testcontainersUsesBomAndVersionlessModules() {
        String kts = readUnit(generateMvcWithTests("gradle", "jpa", "mysql", true), "build.gradle.kts");
        assertThat(kts)
                .contains("val testcontainersVersion = \"1.20.4\"")
                .contains("testImplementation(platform(\"org.testcontainers:testcontainers-bom:$testcontainersVersion\"))")
                .contains("testImplementation(\"org.testcontainers:junit-jupiter\")")
                .contains("testImplementation(\"org.testcontainers:mysql\")")
                .doesNotContain("org.testcontainers:junit-jupiter:1.20.4")
                .doesNotContain("org.testcontainers:mysql:1.20.4");
    }

    @Test
    void gradle_settingsHasRootProjectName() {
        String settings = readUnit(generateMvc("gradle", "jpa"), "settings.gradle.kts");
        assertThat(settings).contains("rootProject.name = \"app\"");
    }

    @Test
    void gradle_githubCiUsesCompileJavaBootJarAndTest_notMaven() {
        String ci = readUnit(generateMvcWithTooling("gradle", "jpa", false, "github"),
                ".github/workflows/ci.yml");

        assertThat(ci)
                .contains("gradle/actions/setup-gradle@v4")
                .contains("gradle --no-daemon compileJava")
                .contains("gradle --no-daemon -x test bootJar")
                .contains("gradle --no-daemon test")
                .doesNotContain("mvn ")
                .doesNotContain("cache: maven");
    }

    @Test
    void gradle_gitlabCiUsesCompileJavaBootJarAndTest_notMaven() {
        String ci = readUnit(generateMvcWithTooling("gradle", "jpa", false, "gitlab"),
                ".gitlab-ci.yml");

        assertThat(ci)
                .contains("image: gradle:8.11-jdk17")
                .contains("gradle --no-daemon compileJava")
                .contains("gradle --no-daemon -x test bootJar")
                .contains("gradle --no-daemon test")
                .contains("build/libs/*.jar")
                .doesNotContain("mvn ")
                .doesNotContain("MAVEN_")
                .doesNotContain("target/*.jar");
    }

    @Test
    void gradle_dockerfileUsesCompileJavaBootJarAndNoWrapperOrMaven() {
        String dockerfile = readUnit(generateMvcWithTooling("gradle", "jpa", true, "none"),
                "Dockerfile");

        assertThat(dockerfile)
                .contains("FROM gradle:8.11-jdk17 AS build")
                .contains("gradle --no-daemon compileJava")
                .contains("gradle --no-daemon -x test bootJar")
                .contains("COPY --from=build /workspace/build/libs/*.jar /app/app.jar")
                .doesNotContain("./gradlew")
                .doesNotContain("COPY gradle ./gradle")
                .doesNotContain("COPY gradlew ./")
                .doesNotContain("mvn ");
    }

    // --------------------------------------------------------- render parity

    /**
     * For each MVC + persistence case, every {@code groupId:artifactId} we emit
     * in the pom must also appear in the gradle file. Catches "I added a dep
     * to the pom but forgot the gradle file" PRs.
     *
     * <p>Strips version coordinates and scope tags, so we only compare the dep
     * <em>identity</em>, not the exact dependency-syntax shape.</p>
     */
    @Test
    void renderParity_mvc_jpa() {
        assertGroupArtifactParity("mvc", "jpa", "postgresql");
    }

    @Test
    void renderParity_mvc_mybatis() {
        assertGroupArtifactParity("mvc", "mybatis", "postgresql");
    }

    @Test
    void renderParity_mvc_jooq() {
        assertGroupArtifactParity("mvc", "jooq", "postgresql");
    }

    @Test
    void renderParity_hex_jpa() {
        assertGroupArtifactParity("hexagonal", "jpa", "postgresql");
    }

    @Test
    void renderParity_ddd_jpa() {
        assertGroupArtifactParity("ddd", "jpa", "postgresql");
    }

    private static void assertGroupArtifactParity(String architecture, String persistence, String dbDriver) {
        List<GeneratedUnit> mavenUnits = generate(architecture, "maven", persistence, dbDriver);
        List<GeneratedUnit> gradleUnits = generate(architecture, "gradle", persistence, dbDriver);

        String pom = readUnit(mavenUnits, "pom.xml");
        String kts = readUnit(gradleUnits, "build.gradle.kts");

        var pomDeps = extractMavenDeps(pom);
        var ktsDeps = extractGradleDeps(kts);

        // Every pom dep must show up in the gradle file. Reverse direction is
        // looser because Gradle's `mavenCentral()` repository declaration and
        // `tasks.withType<Test> { useJUnitPlatform() }` plumbing don't have
        // pom equivalents.
        assertThat(ktsDeps)
                .as("%s + %s + %s — gradle deps must include every maven groupId:artifactId",
                        architecture, persistence, dbDriver)
                .containsAll(pomDeps);
    }

    /** Extracts {@code groupId:artifactId} pairs from a pom.xml string. */
    private static java.util.Set<String> extractMavenDeps(String pom) {
        Pattern p = Pattern.compile(
                "<dependency>\\s*(?:<!--.*?-->\\s*)*<groupId>([^<]+)</groupId>\\s*<artifactId>([^<]+)</artifactId>",
                Pattern.DOTALL);
        Matcher m = p.matcher(pom);
        var out = new java.util.LinkedHashSet<String>();
        while (m.find()) {
            out.add(m.group(1).trim() + ":" + m.group(2).trim());
        }
        return out;
    }

    /** Extracts {@code groupId:artifactId} pairs from a Gradle Kotlin DSL build script. */
    private static java.util.Set<String> extractGradleDeps(String kts) {
        // Match implementation/runtimeOnly/testImplementation/compileOnly/annotationProcessor/jooqGenerator
        // calls, both with and without an explicit version segment. Gradle platforms
        // wrap the dependency notation in platform("...").
        Pattern p = Pattern.compile(
                "(?:implementation|runtimeOnly|testImplementation|compileOnly|annotationProcessor|jooqGenerator)" +
                        "\\((?:platform\\()?\"([^:\"]+):([^:\"]+)(?::[^\"]*)?\"\\)?\\)");
        Matcher m = p.matcher(kts);
        return m.results()
                .map(r -> r.group(1).trim() + ":" + r.group(2).trim())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    // --------------------------------------------------------- helpers

    private static List<GeneratedUnit> generateMvc(String buildTool, String persistence) {
        return generate("mvc", buildTool, persistence, "postgresql");
    }

    private static List<GeneratedUnit> generateMvc(String buildTool, String persistence, String dbDriver) {
        return generate("mvc", buildTool, persistence, dbDriver);
    }

    private static List<GeneratedUnit> generateMvcWithTests(String buildTool, String persistence,
                                                             String dbDriver, boolean tests) {
        GeneratorContext ctx = ctx("mvc", buildTool, persistence, dbDriver, tests);
        return new MvcGenerator(new TemplateEngine(null), ctx).generate(schema());
    }

    private static List<GeneratedUnit> generateMvcWithTooling(String buildTool, String persistence,
                                                               boolean docker, String ciStyle) {
        GeneratorContext ctx = ctx("mvc", buildTool, persistence, "postgresql",
                false, docker, ciStyle);
        return new MvcGenerator(new TemplateEngine(null), ctx).generate(schema());
    }

    private static List<GeneratedUnit> generateHex(String buildTool, String persistence) {
        return generate("hexagonal", buildTool, persistence, "postgresql");
    }

    private static List<GeneratedUnit> generateDdd(String buildTool, String persistence) {
        return generate("ddd", buildTool, persistence, "postgresql");
    }

    private static List<GeneratedUnit> generate(String architecture, String buildTool,
                                                 String persistence, String dbDriver) {
        GeneratorContext ctx = ctx(architecture, buildTool, persistence, dbDriver, false);
        if ("hexagonal".equals(architecture)) {
            return new HexagonalGenerator(new TemplateEngine(null), ctx).generate(schema());
        }
        if ("ddd".equals(architecture)) {
            return new DddGenerator(new TemplateEngine(null), ctx).generate(schema());
        }
        return new MvcGenerator(new TemplateEngine(null), ctx).generate(schema());
    }

    private static GeneratorContext ctx(String architecture, String buildTool,
                                         String persistence, String dbDriver, boolean tests) {
        return ctx(architecture, buildTool, persistence, dbDriver, tests, false, "none");
    }

    private static GeneratorContext ctx(String architecture, String buildTool,
                                         String persistence, String dbDriver, boolean tests,
                                         boolean docker, String ciStyle) {
        return new GeneratorContext(
                "com.example.shop", "app", "com.example",
                "3.3.5", "17", true,
                architecture, persistence, "xml", false, "none", "constructor",
                "jakarta", "class", "separate", "problemdetail",
                UmabootConfig.AuditOptions.defaults(),
                UmabootConfig.SoftDeleteOptions.defaults(),
                docker ? new UmabootConfig.DockerOptions(true, "eclipse-temurin:17-jre-alpine", 8080)
                       : UmabootConfig.DockerOptions.defaults(),
                new UmabootConfig.CiOptions(ciStyle),
                UmabootConfig.LoggingOptions.defaults(),
                tests ? new UmabootConfig.TestOptions(true)
                      : UmabootConfig.TestOptions.defaults(),
                "offset",
                UmabootConfig.SecurityOptions.defaults(),
                UmabootConfig.DddOptions.defaults(),
                false, dbDriver, null, null, "", null, buildTool);
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

    private static void assertHasFile(List<GeneratedUnit> units, String path) {
        assertThat(units).extracting(GeneratedUnit::relativePath).contains(path);
    }

    private static void assertNoFile(List<GeneratedUnit> units, String path) {
        assertThat(units).extracting(GeneratedUnit::relativePath).doesNotContain(path);
    }

    private static String readUnit(List<GeneratedUnit> units, String path) {
        return units.stream()
                .filter(u -> u.relativePath().equals(path))
                .findFirst()
                .map(GeneratedUnit::content)
                .orElseThrow(() -> new AssertionError("Missing: " + path));
    }
}

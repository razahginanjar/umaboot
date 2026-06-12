package io.umaboot.core.overlay;

import io.umaboot.core.config.UmabootConfig;
import io.umaboot.core.generator.GeneratorContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BuildFileDependencyPlannerTest {

    @Test
    void mavenPlanFlagsWrongScopeOpenApiAndProducesPomPatch(@TempDir Path output) throws Exception {
        Files.writeString(output.resolve("pom.xml"), """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.springdoc</groupId>
                            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """);

        BuildFileDependencyPlanner.Plan plan = new BuildFileDependencyPlanner()
                .plan(output, context("maven", "annotation", false, false));

        assertThat(plan.available()).isTrue();
        assertThat(plan.hasMissingDependencies()).isTrue();
        assertThat(plan.missingFindings())
                .anySatisfy(finding -> {
                    assertThat(finding.dependency().groupId()).isEqualTo("org.springdoc");
                    assertThat(finding.dependency().artifactId()).isEqualTo("springdoc-openapi-starter-webmvc-ui");
                    assertThat(finding.status()).isEqualTo(BuildFileDependencyPlanner.Status.WRONG_SCOPE);
                });
        assertThat(plan.patchUnits()).hasSize(1);
        assertThat(plan.patchUnits().get(0).relativePath()).isEqualTo("pom.xml");
        assertThat(plan.patchUnits().get(0).content())
                .contains("<artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>")
                .contains("<version>2.6.0</version>");
    }

    @Test
    void gradleKotlinPlanAcceptsDependenciesInCorrectConfigurations(@TempDir Path output) throws Exception {
        Files.writeString(output.resolve("build.gradle.kts"), """
                plugins {
                    id("org.springframework.boot") version "3.3.5"
                }

                dependencies {
                    implementation("org.springframework.boot:spring-boot-starter-web")
                    implementation("org.springframework.boot:spring-boot-starter-validation")
                    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")
                    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
                    runtimeOnly("org.postgresql:postgresql")
                }
                """);

        BuildFileDependencyPlanner.Plan plan = new BuildFileDependencyPlanner()
                .plan(output, context("gradle", "annotation", false, false));

        assertThat(plan.available()).isTrue();
        assertThat(plan.hasMissingDependencies()).isFalse();
        assertThat(plan.patchUnits()).isEmpty();
    }

    @Test
    void mavenPlanForSpringBoot273MysqlUsesLegacyConnectorCoordinate(@TempDir Path output) throws Exception {
        Files.writeString(output.resolve("pom.xml"), """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                    </dependencies>
                </project>
                """);

        BuildFileDependencyPlanner.Plan plan = new BuildFileDependencyPlanner()
                .plan(output, context("maven", "none", false, false,
                        "2.7.3", "1.8", "mysql", "jpa"));

        assertThat(plan.missingFindings())
                .anySatisfy(finding -> {
                    assertThat(finding.dependency().groupId()).isEqualTo("mysql");
                    assertThat(finding.dependency().artifactId()).isEqualTo("mysql-connector-java");
                });
        assertThat(plan.patchUnits()).hasSize(1);
        assertThat(plan.patchUnits().get(0).content())
                .contains("<groupId>mysql</groupId>")
                .contains("<artifactId>mysql-connector-java</artifactId>")
                .doesNotContain("<groupId>com.mysql</groupId>")
                .doesNotContain("<artifactId>mysql-connector-j</artifactId>");
    }

    @Test
    void mavenPlanForSpringBoot2SqliteJpaUsesVersionedHibernate5Dialect(@TempDir Path output) throws Exception {
        Files.writeString(output.resolve("pom.xml"), """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                    </dependencies>
                </project>
                """);

        BuildFileDependencyPlanner.Plan plan = new BuildFileDependencyPlanner()
                .plan(output, context("maven", "none", false, false,
                        "2.7.18", "1.8", "sqlite", "jpa"));

        assertThat(plan.missingFindings())
                .anySatisfy(finding -> {
                    assertThat(finding.dependency().groupId()).isEqualTo("com.github.gwenn");
                    assertThat(finding.dependency().artifactId()).isEqualTo("sqlite-dialect");
                    assertThat(finding.dependency().version()).isEqualTo("0.1.4");
                });
        assertThat(plan.patchUnits()).hasSize(1);
        assertThat(plan.patchUnits().get(0).content())
                .contains("<groupId>org.xerial</groupId>")
                .contains("<artifactId>sqlite-jdbc</artifactId>")
                .contains("<groupId>com.github.gwenn</groupId>")
                .contains("<artifactId>sqlite-dialect</artifactId>")
                .contains("<version>0.1.4</version>")
                .doesNotContain("<artifactId>hibernate-community-dialects</artifactId>");
    }

    @Test
    void overlayPlannerIncludesBuildFilePatchInPreviewButDoesNotWriteItAsNewFile(@TempDir Path output) throws Exception {
        Files.writeString(output.resolve("pom.xml"), """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """);

        OverlayPlan plan = new OverlayPlanner().plan(
                List.of(),
                output,
                context("maven", "annotation", false, false));

        assertThat(plan.diff().modified()).contains("pom.xml");
        assertThat(plan.newUnits()).isEmpty();
        assertThat(plan.previewUnits())
                .extracting(unit -> unit.relativePath())
                .contains("pom.xml");
        assertThat(plan.dependencies().hasPatch()).isTrue();
    }

    private static GeneratorContext context(String buildTool, String openApiStyle,
                                            boolean useLombok, boolean useMapStruct) {
        return context(buildTool, openApiStyle, useLombok, useMapStruct,
                "3.3.5", "17", "postgresql", "jpa");
    }

    private static GeneratorContext context(String buildTool, String openApiStyle,
                                            boolean useLombok, boolean useMapStruct,
                                            String springBootVersion, String javaVersion,
                                            String dbDriver, String persistence) {
        String exceptionStyle = springBootVersion.startsWith("2.") ? "envelope" : "problemdetail";
        return new GeneratorContext(
                "com.example.app",
                "app",
                "com.example",
                springBootVersion,
                javaVersion,
                useLombok,
                null,
                "mvc",
                persistence,
                "xml",
                useMapStruct,
                openApiStyle,
                "constructor",
                "jakarta",
                "class",
                "separate",
                exceptionStyle,
                UmabootConfig.AuditOptions.defaults(),
                UmabootConfig.SoftDeleteOptions.defaults(),
                UmabootConfig.DockerOptions.defaults(),
                UmabootConfig.CiOptions.defaults(),
                UmabootConfig.LoggingOptions.defaults(),
                UmabootConfig.TestOptions.defaults(),
                UmabootConfig.MigrationOptions.defaults(),
                "offset",
                UmabootConfig.SecurityOptions.defaults(),
                UmabootConfig.DddOptions.defaults(),
                true,
                dbDriver,
                null,
                null,
                UmabootConfig.ApplicationConfigOptions.defaults(),
                "",
                Map.of(),
                buildTool);
    }
}

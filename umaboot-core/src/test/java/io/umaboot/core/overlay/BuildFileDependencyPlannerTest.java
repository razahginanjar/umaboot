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
        return new GeneratorContext(
                "com.example.app",
                "app",
                "com.example",
                "3.3.5",
                "17",
                useLombok,
                null,
                "mvc",
                "jpa",
                "xml",
                useMapStruct,
                openApiStyle,
                "constructor",
                "jakarta",
                "class",
                "separate",
                "problemdetail",
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
                "postgresql",
                null,
                null,
                UmabootConfig.ApplicationConfigOptions.defaults(),
                "",
                Map.of(),
                buildTool);
    }
}

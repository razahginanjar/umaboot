package io.umaboot.core.overlay;

import io.umaboot.core.config.UmabootConfig;
import io.umaboot.core.generator.GeneratedUnit;
import io.umaboot.core.generator.GeneratorContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OverlayPlannerTest {

    @Test
    void classifiesGeneratedFilesAndReturnsOnlyNewUnits(@TempDir Path output) throws Exception {
        Files.writeString(output.resolve("same.txt"), "same\n");
        Files.writeString(output.resolve("changed.txt"), "manual edit\n");

        List<GeneratedUnit> units = List.of(
                new GeneratedUnit("new.txt", "new\n"),
                new GeneratedUnit("same.txt", "same\n"),
                new GeneratedUnit("changed.txt", "generated\n"));

        OverlayPlan plan = new OverlayPlanner().plan(
                units,
                output,
                GeneratorContext.defaults("com.example.app", "app"));

        assertThat(plan.diff().added()).containsExactly("new.txt");
        assertThat(plan.diff().unchanged()).containsExactly("same.txt");
        assertThat(plan.diff().modified()).containsExactly("changed.txt");
        assertThat(plan.newUnits())
                .extracting(GeneratedUnit::relativePath)
                .containsExactly("new.txt");
        assertThat(plan.hasModifiedFiles()).isTrue();
        assertThat(plan.requirements()).isNotEmpty();
    }

    @Test
    void includesApplicationConfigPatchInPreviewButDoesNotAutoWriteIt(@TempDir Path output) throws Exception {
        Path yml = output.resolve("src/main/resources/application.yml");
        Files.createDirectories(yml.getParent());
        Files.writeString(yml, "spring:\n");

        OverlayPlan plan = new OverlayPlanner().plan(
                List.of(),
                output,
                mybatisContext());

        assertThat(plan.diff().modified()).contains("src/main/resources/application.yml");
        assertThat(plan.newUnits()).isEmpty();
        assertThat(plan.previewUnits())
                .extracting(GeneratedUnit::relativePath)
                .contains("src/main/resources/application.yml");
        assertThat(plan.applicationConfig().hasPatch()).isTrue();
        assertThat(plan.needsPreviewMerge()).isTrue();
    }

    private static GeneratorContext mybatisContext() {
        return new GeneratorContext(
                "com.example.app",
                "app",
                "com.example",
                "3.3.5",
                "17",
                true,
                null,
                "mvc",
                "mybatis",
                "xml",
                false,
                "none",
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
                "maven");
    }
}

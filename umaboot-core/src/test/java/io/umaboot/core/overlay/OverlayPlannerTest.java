package io.umaboot.core.overlay;

import io.umaboot.core.generator.GeneratedUnit;
import io.umaboot.core.generator.GeneratorContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
}

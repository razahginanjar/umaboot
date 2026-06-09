package io.umaboot.core.standalone;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StandaloneOutputSafetyTest {

    @Test
    void warnPolicyBlocksForeignProjectOutput(@TempDir Path output) throws Exception {
        Files.writeString(output.resolve("pom.xml"), "<project/>");

        StandaloneOutputSafety.Plan plan = StandaloneOutputSafety.inspect(output, "warn");

        assertThat(plan.existingProject()).isTrue();
        assertThat(plan.requiresWarning()).isTrue();
        assertThat(plan.shouldBlock()).isTrue();
        assertThat(plan.markerSummary()).contains("pom.xml");
    }

    @Test
    void umabootMarkerAllowsNormalOverwrite(@TempDir Path output) throws Exception {
        Files.writeString(output.resolve("pom.xml"), "<project/>");
        StandaloneOutputSafety.writeMarker(output);

        StandaloneOutputSafety.Plan plan = StandaloneOutputSafety.inspect(output, "warn");

        assertThat(plan.existingProject()).isTrue();
        assertThat(plan.umabootMarked()).isTrue();
        assertThat(plan.requiresWarning()).isFalse();
        assertThat(plan.shouldOverwrite()).isTrue();
    }

    @Test
    void cleanDeletesOutputButRefusesProjectRootMarkers(@TempDir Path output) throws Exception {
        Files.writeString(output.resolve("old.txt"), "old");
        StandaloneOutputSafety.Plan cleanPlan = StandaloneOutputSafety.inspect(output, "clean");

        StandaloneOutputSafety.clean(cleanPlan);

        assertThat(Files.exists(output.resolve("old.txt"))).isFalse();

        Files.writeString(output.resolve("umaboot.yaml"), "generation: {}\n");
        StandaloneOutputSafety.Plan protectedPlan = StandaloneOutputSafety.inspect(output, "clean");

        assertThatThrownBy(() -> StandaloneOutputSafety.clean(protectedPlan))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Refusing to clean");
    }
}

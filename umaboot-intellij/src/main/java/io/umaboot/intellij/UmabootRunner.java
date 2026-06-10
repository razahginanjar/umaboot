package io.umaboot.intellij;

import io.umaboot.core.GenerationPipeline;
import io.umaboot.core.architecture.ArchitectureRenderer;
import io.umaboot.core.architecture.ArchitectureRenderers;
import io.umaboot.core.config.UmabootConfig;
import io.umaboot.core.config.UmabootConfigLoader;
import io.umaboot.core.generator.GeneratedUnit;
import io.umaboot.core.generator.GeneratorContext;
import io.umaboot.core.overlay.OverlayPlan;
import io.umaboot.core.overlay.OverlayPlanner;
import io.umaboot.core.standalone.StandaloneOutputSafety;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runs the generation pipeline against the workspace's umaboot.yaml.
 */
final class UmabootRunner {

    record Result(int fileCount, Path outputDir, String architecture, String persistence,
                  String mode, boolean autoOverlay, List<String> warnings,
                  int overlayModifiedCount, int overlayUnchangedCount,
                  boolean overlayPreviewRequired, int overlayPreviewMergeCount,
                  List<String> overlayModifiedFiles, List<String> overlayRequirements) {}

    record Plan(List<GeneratedUnit> units, Path outputDir, String architecture, String persistence,
                String mode, String existingPolicy, boolean autoOverlay,
                GeneratorContext ctx, List<String> warnings) {}

    Result run(Path configFile) throws Exception {
        return run(configFile, null);
    }

    Result run(Path configFile, String existingPolicyOverride) throws Exception {
        Plan plan = prepare(configFile);

        ArchitectureRenderer renderer = ArchitectureRenderers.forContext(plan.ctx());

        if (plan.ctx().overlay()) {
            OverlayPlan overlayPlan = new OverlayPlanner().plan(plan.units(), plan.outputDir(), plan.ctx());
            if (overlayPlan.hasNewFiles()) {
                renderer.render(overlayPlan.newUnits(), plan.outputDir());
            }
            List<String> requirements = new ArrayList<>(overlayPlan.requirements());
            requirements.addAll(overlayPlan.dependencies().messages());
            if (overlayPlan.dependencies().hasPatch()) {
                requirements.add("Build-file dependency patch available in Preview / Merge: "
                        + overlayPlan.dependencies().relativePath());
            }
            requirements.addAll(overlayPlan.applicationConfig().messages());

            return new Result(
                    overlayPlan.newCount(),
                    plan.outputDir(),
                    plan.architecture(),
                    plan.persistence(),
                    plan.mode(),
                    plan.autoOverlay(),
                    plan.warnings(),
                    overlayPlan.modifiedCount(),
                    overlayPlan.unchangedCount(),
                    overlayPlan.needsPreviewMerge(),
                    overlayPlan.previewMergeCount(),
                    overlayPlan.diff().modified(),
                    requirements);
        }

        String existingPolicy = existingPolicyOverride == null
                ? plan.existingPolicy()
                : new UmabootConfig.OutputOptions("standalone", existingPolicyOverride).existingPolicy();
        StandaloneOutputSafety.Plan standalonePlan =
                StandaloneOutputSafety.inspect(plan.outputDir(), existingPolicy);
        if (standalonePlan.shouldBlock()) {
            throw new IllegalStateException("Standalone output already looks like an existing project at "
                    + standalonePlan.outputDir() + " (markers: " + standalonePlan.markerSummary() + ")");
        }
        if (standalonePlan.shouldClean()) {
            StandaloneOutputSafety.clean(standalonePlan);
        }
        renderer.render(plan.units(), plan.outputDir());
        StandaloneOutputSafety.writeMarker(plan.outputDir());

        return new Result(
                plan.units().size(),
                plan.outputDir(),
                plan.architecture(),
                plan.persistence(),
                plan.mode(),
                plan.autoOverlay(),
                plan.warnings(),
                0,
                0,
                false,
                0,
                List.of(),
                List.of());
    }

    StandaloneOutputSafety.Plan inspectStandaloneOutput(Path configFile) throws Exception {
        if (!Files.exists(configFile)) {
            throw new IllegalArgumentException("Config not found: " + configFile);
        }
        UmabootConfig config = UmabootConfigLoader.load(configFile);
        if (shouldAutoOverlay(config, configFile)) {
            config = withOverlay(config);
        }
        if (!config.generation().output().isStandalone()) {
            return null;
        }
        Path output = resolveOutputDir(config, configFile);
        return StandaloneOutputSafety.inspect(output, config.generation().output().existingPolicy());
    }

    Plan prepare(Path configFile) throws Exception {
        if (!Files.exists(configFile)) {
            throw new IllegalArgumentException("Config not found: " + configFile);
        }
        UmabootConfig config = UmabootConfigLoader.load(configFile);

        boolean autoOverlay = false;
        if (shouldAutoOverlay(config, configFile)) {
            config = withOverlay(config);
            autoOverlay = true;
        }

        GenerationPipeline.Result result = GenerationPipeline.run(config, null);
        Path output = resolveOutputDir(config, configFile);

        return new Plan(
                result.units(),
                output,
                result.ctx().architecture(),
                result.ctx().persistence(),
                result.ctx().overlay() ? "overlay" : "standalone",
                config.generation().output().existingPolicy(),
                autoOverlay,
                result.ctx(),
                result.warnings());
    }

    private static boolean shouldAutoOverlay(UmabootConfig config, Path configFile) {
        Path projectRoot = configFile.getParent();
        return config.generation().output().isStandalone()
                && !hasExplicitOutputMode(configFile)
                && projectRoot != null
                && Files.exists(projectRoot.resolve("pom.xml"));
    }

    private static UmabootConfig withOverlay(UmabootConfig config) {
        var gen = config.generation();
        var newOutput = new UmabootConfig.OutputOptions("overlay");
        var newGen = new UmabootConfig.Generation(
                gen.architecture(),
                gen.persistence(),
                gen.basePackage(),
                gen.projectName(),
                gen.projectGroup(),
                gen.springBootVersion(),
                gen.javaVersion(),
                gen.useLombok(),
                gen.lombokVersion(),
                gen.openapi(),
                gen.injection(),
                gen.validation(),
                gen.dto(),
                gen.exception(),
                gen.audit(),
                gen.softDelete(),
                gen.docker(),
                gen.ci(),
                gen.logging(),
                gen.tests(),
                gen.migrations(),
                gen.pagination(),
                gen.security(),
                ".",
                gen.jpa(),
                gen.mybatis(),
                gen.tables(),
                gen.ddd(),
                newOutput,
                gen.applicationConfig(),
                gen.schemaFile(),
                gen.schemaDialect(),
                gen.buildTool());
        return new UmabootConfig(config.connection(), newGen);
    }

    private static boolean hasExplicitOutputMode(Path configFile) {
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        try (var reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            Object loaded = yaml.load(reader);
            if (!(loaded instanceof Map<?, ?> root)) {
                return false;
            }
            Object generationValue = root.get("generation");
            if (!(generationValue instanceof Map<?, ?> generation)) {
                return false;
            }
            Object outputValue = generation.get("output");
            if (!(outputValue instanceof Map<?, ?> output)) {
                return false;
            }
            Object mode = output.get("mode");
            return mode != null && !mode.toString().isBlank();
        } catch (java.io.IOException e) {
            throw new UncheckedIOException("Failed to read " + configFile, e);
        }
    }

    private static Path resolveOutputDir(UmabootConfig config, Path configFile) {
        return io.umaboot.core.config.OutputDirResolver.resolve(config, configFile);
    }
}

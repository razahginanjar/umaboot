package io.umaboot.intellij;

import io.umaboot.core.GenerationPipeline;
import io.umaboot.core.architecture.ArchitectureRenderer;
import io.umaboot.core.architecture.ArchitectureRenderers;
import io.umaboot.core.config.ApplicationConfigMerger;
import io.umaboot.core.config.UmabootConfig;
import io.umaboot.core.config.UmabootConfigLoader;
import io.umaboot.core.generator.GeneratedUnit;
import io.umaboot.core.generator.GeneratorContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Runs the generation pipeline against the workspace's umaboot.yaml.
 */
final class UmabootRunner {

    record Result(int fileCount, Path outputDir, String architecture, String persistence,
                  String mode, boolean autoOverlay) {}

    record Plan(List<GeneratedUnit> units, Path outputDir, String architecture, String persistence,
                String mode, boolean autoOverlay, GeneratorContext ctx) {}

    Result run(Path configFile) throws Exception {
        Plan plan = prepare(configFile);

        ArchitectureRenderer renderer = ArchitectureRenderers.forContext(plan.ctx());
        renderer.render(plan.units(), plan.outputDir());

        if (plan.ctx().overlay()) {
            ApplicationConfigMerger.merge(plan.outputDir(), plan.ctx());
        }

        return new Result(
                plan.units().size(),
                plan.outputDir(),
                plan.architecture(),
                plan.persistence(),
                plan.mode(),
                plan.autoOverlay());
    }

    Plan prepare(Path configFile) throws Exception {
        if (!Files.exists(configFile)) {
            throw new IllegalArgumentException("Config not found: " + configFile);
        }
        UmabootConfig config = UmabootConfigLoader.load(configFile);

        Path projectRoot = configFile.getParent();
        boolean autoOverlay = false;
        if (config.generation().output().isStandalone()
                && projectRoot != null
                && Files.exists(projectRoot.resolve("pom.xml"))) {
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
                autoOverlay,
                result.ctx());
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

    private static Path resolveOutputDir(UmabootConfig config, Path configFile) {
        return io.umaboot.core.config.OutputDirResolver.resolve(config, configFile);
    }
}

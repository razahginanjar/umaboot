package io.umaboot.intellij;

import io.umaboot.core.GenerationPipeline;
import io.umaboot.core.architecture.ArchitectureRenderer;
import io.umaboot.core.architecture.ArchitectureRenderers;
import io.umaboot.core.config.ApplicationConfigMerger;
import io.umaboot.core.config.UmabootConfig;
import io.umaboot.core.config.UmabootConfigLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Runs the generation pipeline against the workspace's {@code umaboot.yaml}.
 *
 * <p>UX heuristic: if the user did <em>not</em> explicitly set
 * {@code generation.output.mode} in their config and the project root already
 * contains a {@code pom.xml}, this runner overlays generation onto the
 * existing project (skipping {@code pom.xml}, {@code Application.java},
 * {@code application.yml}, and {@code GlobalExceptionHandler.java}). This
 * matches the expected behavior of "generate into the project I have open"
 * without forcing the user to duplicate config every time.</p>
 */
final class UmabootRunner {

    /**
     * Result returned to the caller for status reporting.
     *
     * @param fileCount     number of files generated
     * @param outputDir     absolute path written to
     * @param architecture  the architecture used (mvc/hexagonal/ddd)
     * @param persistence   the persistence backend used (jpa/mybatis/jooq)
     * @param mode          standalone or overlay
     * @param autoOverlay   true when overlay was inferred (not explicitly set)
     */
    record Result(int fileCount, Path outputDir, String architecture, String persistence,
                  String mode, boolean autoOverlay) {}

    Result run(Path configFile) throws Exception {
        if (!Files.exists(configFile)) {
            throw new IllegalArgumentException("Config not found: " + configFile);
        }
        UmabootConfig config = UmabootConfigLoader.load(configFile);

        // Heuristic: if the user accepted the default (standalone) but the
        // project root already has a pom.xml, switch to overlay. Otherwise
        // honor whatever they configured.
        Path projectRoot = configFile.getParent();
        boolean autoOverlay = false;
        if (config.generation().output().isStandalone()
                && projectRoot != null
                && Files.exists(projectRoot.resolve("pom.xml"))) {
            config = withOverlay(config);
            autoOverlay = true;
        }

        GenerationPipeline.Result r = GenerationPipeline.run(config, null);

        Path output = resolveOutputDir(config, configFile);

        ArchitectureRenderer renderer = ArchitectureRenderers.forContext(r.ctx());
        renderer.render(r.units(), output);

        // In overlay mode, append our required entries (e.g. mybatis.mapper-locations)
        // to the user's existing application.yml/.properties — no-op otherwise.
        if (r.ctx().overlay()) {
            ApplicationConfigMerger.merge(output, r.ctx());
        }

        return new Result(
                r.units().size(),
                output,
                r.ctx().architecture(),
                r.ctx().persistence(),
                r.ctx().overlay() ? "overlay" : "standalone",
                autoOverlay);
    }

    /** Returns a new config with overlay mode and outputDir defaulted to "." if unset. */
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
                gen.pagination(),
                gen.security(),
                ".",  // overlay-friendly default
                gen.jpa(),
                gen.mybatis(),
                gen.tables(),
                gen.ddd(),
                newOutput,
                gen.applicationConfig(),
                gen.schemaFile());
        return new UmabootConfig(config.connection(), newGen);
    }

    /** Resolve relative outputDir against the project root (config file's parent). */
    private static Path resolveOutputDir(UmabootConfig config, Path configFile) {
        return io.umaboot.core.config.OutputDirResolver.resolve(config, configFile);
    }
}

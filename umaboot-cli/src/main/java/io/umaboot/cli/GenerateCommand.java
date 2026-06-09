package io.umaboot.cli;

import io.umaboot.core.GenerationPipeline;
import io.umaboot.core.architecture.ArchitectureRenderer;
import io.umaboot.core.architecture.ArchitectureRenderers;
import io.umaboot.core.config.ApplicationConfigMerger;
import io.umaboot.core.config.OutputDirResolver;
import io.umaboot.core.config.UmabootConfig;
import io.umaboot.core.config.UmabootConfigLoader;
import io.umaboot.core.overlay.OverlayPlan;
import io.umaboot.core.overlay.OverlayPlanner;
import io.umaboot.core.standalone.StandaloneOutputSafety;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "generate",
        description = "Introspect a database and generate a Spring Boot CRUD project.",
        mixinStandardHelpOptions = true)
public final class GenerateCommand implements Callable<Integer> {

    private static final Logger LOG = LoggerFactory.getLogger(GenerateCommand.class);

    @Option(names = {"-c", "--config"},
            description = "Path to umaboot.yaml (default: ./umaboot.yaml)",
            defaultValue = "umaboot.yaml")
    Path configFile;

    @Option(names = {"-o", "--output"},
            description = "Override output directory from config")
    Path outputOverride;

    @Option(names = {"--templates"},
            description = "Optional directory of FreeMarker template overrides")
    Path templatesDir;

    @Option(names = {"--existing-policy"},
            description = "Standalone existing-output policy: warn, overwrite, clean, or fail")
    String existingPolicyOverride;

    @Override
    public Integer call() {
        try {
            UmabootConfig config = UmabootConfigLoader.load(configFile);
            LOG.info("Loaded config from {}", configFile);

            GenerationPipeline.Result r = GenerationPipeline.run(config, templatesDir);
            CliWarningPrinter.printSchemaWarnings(r.warnings());
            ArchitectureRenderer renderer = ArchitectureRenderers.forContext(r.ctx());
            Path output = outputOverride != null
                    ? outputOverride.toAbsolutePath().normalize()
                    : OutputDirResolver.resolve(config, configFile);
            if (r.ctx().overlay()) {
                OverlayPlan plan = new OverlayPlanner().plan(r.units(), output, r.ctx());
                if (plan.hasNewFiles()) {
                    renderer.render(plan.newUnits(), output);
                }
                Path mergedFile = ApplicationConfigMerger.merge(output, r.ctx());
                if (mergedFile != null) {
                    LOG.info("Merged Umaboot additions into {}", mergedFile);
                }
                printOverlayPlan(output, plan, mergedFile);
                return plan.hasModifiedFiles() ? 1 : 0;
            }

            String existingPolicy = existingPolicyOverride != null
                    ? new UmabootConfig.OutputOptions("standalone", existingPolicyOverride).existingPolicy()
                    : config.generation().output().existingPolicy();
            StandaloneOutputSafety.Plan standalonePlan =
                    StandaloneOutputSafety.inspect(output, existingPolicy);
            if (standalonePlan.shouldBlock()) {
                printStandaloneWarning(standalonePlan);
                return 1;
            }
            if (standalonePlan.shouldClean()) {
                StandaloneOutputSafety.clean(standalonePlan);
            }
            renderer.render(r.units(), output);
            StandaloneOutputSafety.writeMarker(output);

            System.out.println("Generated " + r.units().size() + " files in " + output.toAbsolutePath()
                    + " [architecture=" + r.ctx().architecture()
                    + ", persistence=" + r.ctx().persistence()
                    + (r.ctx().isMyBatis() ? "/" + r.ctx().mybatisStyle() : "")
                    + (r.ctx().useMapStruct() ? ", mapstruct=on" : "")
                    + (r.ctx().generateOpenApi() ? ", openapi=on" : "")
                    + "]");
            return 0;
        } catch (IllegalArgumentException ex) {
            System.err.println("Configuration error: " + ex.getMessage());
            return 2;
        } catch (Exception ex) {
            LOG.error("Generation failed", ex);
            System.err.println("Generation failed: " + ex.getMessage());
            return 2;
        }
    }

    private static void printStandaloneWarning(StandaloneOutputSafety.Plan plan) {
        System.out.println("STANDALONE_OUTPUT_EXISTS: " + plan.outputDir());
        System.out.println("Standalone output already looks like an existing project.");
        System.out.println("  markers: " + plan.markerSummary());
        System.out.println("No files were written.");
        System.out.println("Use --existing-policy overwrite to replace generated file paths, "
                + "or --existing-policy clean to delete the output contents before generation.");
    }

    private static void printOverlayPlan(Path output, OverlayPlan plan, Path mergedFile) {
        System.out.println("Overlay plan against " + output.toAbsolutePath() + ":");
        System.out.println("  new:       " + plan.newCount());
        System.out.println("  modified:  " + plan.modifiedCount()
                + (plan.hasModifiedFiles() ? " (not overwritten)" : ""));
        System.out.println("  unchanged: " + plan.unchangedCount());
        if (mergedFile != null) {
            System.out.println("  config:    merged Umaboot additions into "
                    + output.toAbsolutePath().relativize(mergedFile.toAbsolutePath().normalize()));
        }

        for (String f : plan.diff().added()) System.out.println("  + " + f);
        for (String f : plan.diff().modified()) System.out.println("  ~ " + f);

        if (!plan.requirements().isEmpty()) {
            System.out.println();
            System.out.println("Overlay requirements to verify in the existing project:");
            for (String requirement : plan.requirements()) {
                System.out.println("  - " + requirement);
            }
        }

        if (plan.hasModifiedFiles()) {
            System.out.println();
            System.out.println("Overlay skipped modified existing files. Use Preview / Merge or 'umaboot diff --unified' before accepting them.");
        }
    }
}

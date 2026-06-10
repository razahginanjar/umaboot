package io.umaboot.cli;

import io.umaboot.core.GenerationPipeline;
import io.umaboot.core.config.OutputDirResolver;
import io.umaboot.core.config.UmabootConfig;
import io.umaboot.core.config.UmabootConfigLoader;
import io.umaboot.core.diff.DiffEngine;
import io.umaboot.core.overlay.OverlayPlan;
import io.umaboot.core.overlay.OverlayPlanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Show what would change if {@code generate} were re-run, without writing.
 *
 * <p>Exit codes:</p>
 * <ul>
 *   <li>0 — no changes</li>
 *   <li>1 — changes detected (added or modified files)</li>
 *   <li>2 — error</li>
 * </ul>
 */
@Command(name = "diff",
        description = "Compare generated output against on-disk files. Returns 1 if there are pending changes.",
        mixinStandardHelpOptions = true)
public final class DiffCommand implements Callable<Integer> {

    private static final Logger LOG = LoggerFactory.getLogger(DiffCommand.class);

    @Option(names = {"-c", "--config"}, description = "Path to umaboot.yaml",
            defaultValue = "umaboot.yaml")
    Path configFile;

    @Option(names = {"-o", "--output"}, description = "Override output directory from config")
    Path outputOverride;

    @Option(names = {"--templates"}, description = "Template-override directory")
    Path templatesDir;

    @Option(names = {"--unified"}, description = "Print unified-diff for each modified file")
    boolean unified;

    @Override
    public Integer call() {
        try {
            UmabootConfig config = UmabootConfigLoader.load(configFile);
            GenerationPipeline.Result r = GenerationPipeline.run(config, templatesDir);
            CliWarningPrinter.printSchemaWarnings(r.warnings());
            Path output = outputOverride != null
                    ? outputOverride.toAbsolutePath().normalize()
                    : OutputDirResolver.resolve(config, configFile);
            OverlayPlan overlayPlan = r.ctx().overlay()
                    ? new OverlayPlanner().plan(r.units(), output, r.ctx())
                    : null;
            DiffEngine.DiffResult diff = overlayPlan == null
                    ? new DiffEngine().diff(r.units(), output)
                    : overlayPlan.diff();

            System.out.println("Diff against " + output.toAbsolutePath() + ":");
            System.out.println("  added:     " + diff.added().size());
            System.out.println("  modified:  " + diff.modified().size());
            System.out.println("  unchanged: " + diff.unchanged().size());

            for (String f : diff.added()) System.out.println("  + " + f);
            for (String f : diff.modified()) System.out.println("  ~ " + f);

            if (overlayPlan != null && !overlayPlan.dependencies().available()) {
                System.out.println();
                System.out.println("Overlay dependency check:");
                System.out.println("  - " + overlayPlan.dependencies().unavailableReason());
            } else if (overlayPlan != null && overlayPlan.dependencies().hasMissingDependencies()) {
                System.out.println();
                System.out.println("Overlay dependency check:");
                for (String message : overlayPlan.dependencies().messages()) {
                    System.out.println("  - " + message);
                }
            }

            if (overlayPlan != null && !overlayPlan.applicationConfig().messages().isEmpty()) {
                System.out.println();
                System.out.println("Overlay application config check:");
                for (String message : overlayPlan.applicationConfig().messages()) {
                    System.out.println("  - " + message);
                }
            }

            if (unified) {
                for (String f : diff.modified()) {
                    System.out.println("\n--- " + f + " ---");
                    diff.perFile().get(f).unifiedDiff().forEach(System.out::println);
                }
            }
            return diff.hasChanges() ? 1 : 0;
        } catch (IllegalArgumentException ex) {
            System.err.println("Configuration error: " + ex.getMessage());
            return 2;
        } catch (Exception ex) {
            LOG.error("Diff failed", ex);
            System.err.println("Diff failed: " + ex.getMessage());
            return 2;
        }
    }
}

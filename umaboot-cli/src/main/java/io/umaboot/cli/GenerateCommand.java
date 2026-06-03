package io.umaboot.cli;

import io.umaboot.core.GenerationPipeline;
import io.umaboot.core.architecture.ArchitectureRenderer;
import io.umaboot.core.architecture.ArchitectureRenderers;
import io.umaboot.core.config.ApplicationConfigMerger;
import io.umaboot.core.config.OutputDirResolver;
import io.umaboot.core.config.UmabootConfig;
import io.umaboot.core.config.UmabootConfigLoader;
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

    @Override
    public Integer call() {
        try {
            UmabootConfig config = UmabootConfigLoader.load(configFile);
            LOG.info("Loaded config from {}", configFile);

            GenerationPipeline.Result r = GenerationPipeline.run(config, templatesDir);
            CliWarningPrinter.printParserWarnings(r.warnings());
            ArchitectureRenderer renderer = ArchitectureRenderers.forContext(r.ctx());
            Path output = outputOverride != null
                    ? outputOverride.toAbsolutePath().normalize()
                    : OutputDirResolver.resolve(config, configFile);
            renderer.render(r.units(), output);

            // In overlay mode, append our required entries (e.g. mybatis.mapper-locations)
            // to the user's existing application.yml/.properties — no-op otherwise.
            if (r.ctx().overlay()) {
                Path mergedFile = ApplicationConfigMerger.merge(output, r.ctx());
                if (mergedFile != null) {
                    LOG.info("Merged Umaboot additions into {}", mergedFile);
                }
            }

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
}

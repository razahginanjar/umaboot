package io.umaboot.cli;

import io.umaboot.core.config.UmabootConfig;
import io.umaboot.core.config.UmabootConfigLoader;
import io.umaboot.core.introspection.SchemaIntrospectionService;
import io.umaboot.core.model.SchemaModel;
import io.umaboot.core.model.TableModel;
import io.umaboot.core.relationship.RelationshipEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Lists the non-junction tables in the configured schema. Output is one
 * table name per line on stdout, suitable for shell pipelines and parsing.
 *
 * <p>Used by the VS Code extension's "Refresh Tables" tree action so users
 * can see which tables Umaboot would generate against without running the
 * full pipeline.</p>
 *
 * <p>Exit codes: {@code 0} success, {@code 1} introspection failed,
 * {@code 2} bad config.</p>
 */
@Command(name = "list-tables",
        description = "List the non-junction tables that Umaboot would generate code for.",
        mixinStandardHelpOptions = true)
public final class ListTablesCommand implements Callable<Integer> {

    private static final Logger LOG = LoggerFactory.getLogger(ListTablesCommand.class);

    @Option(names = {"-c", "--config"},
            description = "Path to umaboot.yaml (default: ./umaboot.yaml)",
            defaultValue = "umaboot.yaml")
    Path configFile;

    @Option(names = "--all",
            description = "Include junction (M:N link) tables in the output. Off by default.")
    boolean includeJunctions;

    @Override
    public Integer call() {
        UmabootConfig config;
        try {
            config = UmabootConfigLoader.load(configFile);
        } catch (IllegalArgumentException ex) {
            System.err.println("Configuration error: " + ex.getMessage());
            return 2;
        }

        try {
            SchemaModel schema = new SchemaIntrospectionService().introspect(config).schema();
            schema = new RelationshipEngine().analyze(schema);
            for (TableModel t : schema.tables()) {
                if (!includeJunctions && t.junction()) continue;
                System.out.println(t.name());
            }
            return 0;
        } catch (Exception ex) {
            LOG.debug("List-tables failed", ex);
            System.err.println("FAIL " + ex.getMessage());
            return 1;
        }
    }
}

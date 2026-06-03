package io.umaboot.cli;

import io.umaboot.core.config.UmabootConfig;
import io.umaboot.core.config.UmabootConfigLoader;
import io.umaboot.core.introspection.JdbcDrivers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.util.concurrent.Callable;

/**
 * Smoke-test the JDBC connection from {@code umaboot.yaml}. Prints the database
 * product name + version on success; non-zero exit on failure with the JDBC
 * error message.
 *
 * <p>Used by the VS Code extension's "Test Connection" tree action and any
 * CI pipeline that wants to validate {@code umaboot.yaml} before running
 * generate.</p>
 *
 * <p>Exit codes: {@code 0} success, {@code 1} connection failed,
 * {@code 2} bad config.</p>
 */
@Command(name = "test-connection",
        description = "Connect to the database from umaboot.yaml and print the product / version.",
        mixinStandardHelpOptions = true)
public final class TestConnectionCommand implements Callable<Integer> {

    private static final Logger LOG = LoggerFactory.getLogger(TestConnectionCommand.class);

    @Option(names = {"-c", "--config"},
            description = "Path to umaboot.yaml (default: ./umaboot.yaml)",
            defaultValue = "umaboot.yaml")
    Path configFile;

    @Override
    public Integer call() {
        UmabootConfig config;
        try {
            config = UmabootConfigLoader.load(configFile);
        } catch (IllegalArgumentException ex) {
            System.err.println("Configuration error: " + ex.getMessage());
            return 2;
        }

        if (config.isSchemaFileMode()) {
            System.out.println("SKIP schemaFile mode: no live database connection configured.");
            return 0;
        }

        JdbcDrivers.registerAll();
        UmabootConfig.Connection conn = config.connection();
        try (Connection c = DriverManager.getConnection(conn.url(), conn.username(), conn.password())) {
            DatabaseMetaData md = c.getMetaData();
            System.out.println("OK " + md.getDatabaseProductName() + " " + md.getDatabaseProductVersion());
            return 0;
        } catch (Exception ex) {
            LOG.debug("Connection test failed", ex);
            System.err.println("FAIL " + ex.getMessage());
            return 1;
        }
    }
}

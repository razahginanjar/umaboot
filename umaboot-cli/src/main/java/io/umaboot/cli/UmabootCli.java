package io.umaboot.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Top-level {@code umaboot} command. Subcommands:
 * <ul>
 *   <li>{@link GenerateCommand generate} — read schema, render project</li>
 *   <li>{@link DiffCommand diff} — compare generated output against on-disk files</li>
 *   <li>{@link ApplyCommand apply} — apply pending changes preserving protected regions</li>
 *   <li>{@link TestConnectionCommand test-connection} — validate the JDBC URL in {@code umaboot.yaml}</li>
 *   <li>{@link ListTablesCommand list-tables} — list the schema's non-junction tables</li>
 * </ul>
 */
@Command(
        name = "umaboot",
        mixinStandardHelpOptions = true,
        version = "Umaboot 0.1.0",
        description = "Generate a Spring Boot CRUD project from a database schema.",
        subcommands = {
                GenerateCommand.class,
                DiffCommand.class,
                ApplyCommand.class,
                TestConnectionCommand.class,
                ListTablesCommand.class
        }
)
public final class UmabootCli implements Runnable {

    @Override
    public void run() {
        // No subcommand: print usage and exit non-zero
        CommandLine.usage(this, System.out);
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new UmabootCli()).execute(args);
        System.exit(exitCode);
    }
}

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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final Pattern CREATE_TABLE_NAME = Pattern.compile(
            "\\bCREATE\\s+(?:OR\\s+REPLACE\\s+)?(?:UNLOGGED\\s+)?"
                    + "(?:(?:GLOBAL|LOCAL)\\s+TEMPORARY\\s+|TEMPORARY\\s+|TEMP\\s+)?"
                    + "TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([^\\s(]+)",
            Pattern.CASE_INSENSITIVE);

    @Option(names = {"-c", "--config"},
            description = "Path to umaboot.yaml (default: ./umaboot.yaml)",
            defaultValue = "umaboot.yaml")
    Path configFile;

    @Option(names = "--all",
            description = "Include junction (M:N link) tables in the output. Off by default.")
    boolean includeJunctions;

    @Option(names = "--raw",
            description = "Skip relationship analysis before listing tables. Useful for script-mode UI refresh.")
    boolean raw;

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
            SchemaIntrospectionService.Result source = new SchemaIntrospectionService().introspect(config);
            SchemaModel schema = source.schema();
            if (!raw) {
                schema = new RelationshipEngine().analyze(schema);
            }
            List<String> tableNames = tableNames(schema);
            if (tableNames.isEmpty() && raw && config.isSchemaFileMode()) {
                tableNames = fallbackTableNames(source.schemaFileSql());
            }
            for (String tableName : tableNames) {
                TableModel t = schema.findTable(tableName);
                if (!includeJunctions && t != null && t.junction()) continue;
                System.out.println(tableName);
            }
            return 0;
        } catch (Exception ex) {
            LOG.debug("List-tables failed", ex);
            System.err.println("FAIL " + ex.getMessage());
            return 1;
        }
    }

    private static List<String> tableNames(SchemaModel schema) {
        List<String> out = new ArrayList<>();
        for (TableModel table : schema.tables()) {
            out.add(table.name());
        }
        return out;
    }

    private static List<String> fallbackTableNames(String sql) {
        if (sql == null || sql.isBlank()) return List.of();
        String text = stripSqlComments(sql);
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = CREATE_TABLE_NAME.matcher(text);
        while (matcher.find()) {
            String name = normalizeTableName(matcher.group(1));
            if (!name.isBlank()) {
                names.add(name);
            }
        }
        return List.copyOf(names);
    }

    private static String stripSqlComments(String sql) {
        String withoutBlockComments = sql.replaceAll("(?s)/\\*.*?\\*/", " ");
        return withoutBlockComments.replaceAll("(?m)--.*$", " ");
    }

    private static String normalizeTableName(String raw) {
        String name = raw == null ? "" : raw.trim();
        while (name.endsWith(";") || name.endsWith(",")) {
            name = name.substring(0, name.length() - 1).trim();
        }
        name = name
                .replace("[", "")
                .replace("]", "")
                .replace("`", "")
                .replace("\"", "");
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            name = name.substring(dot + 1);
        }
        return name.trim();
    }
}

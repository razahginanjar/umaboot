package io.umaboot.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.umaboot.core.config.UmabootConfig;
import io.umaboot.core.config.UmabootConfigLoader;
import io.umaboot.core.generator.JavaTypeMapper;
import io.umaboot.core.introspection.SchemaIntrospectionService;
import io.umaboot.core.model.ColumnModel;
import io.umaboot.core.model.SchemaModel;
import io.umaboot.core.model.TableModel;
import io.umaboot.core.relationship.RelationshipEngine;
import io.umaboot.core.template.Naming;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Emits schema metadata as JSON for IDE clients that need table/column details.
 */
@Command(name = "describe-schema",
        description = "Describe tables and columns as JSON for IDE table customization UIs.",
        mixinStandardHelpOptions = true)
public final class DescribeSchemaCommand implements Callable<Integer> {

    private static final Logger LOG = LoggerFactory.getLogger(DescribeSchemaCommand.class);

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
            SchemaIntrospectionService.Result source = new SchemaIntrospectionService().introspect(config);
            CliWarningPrinter.printSchemaWarnings(source.warnings());
            SchemaModel schema = new RelationshipEngine().analyze(source.schema());
            Map<String, Object> payload = describe(config, source, schema);
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(System.out, payload);
            System.out.println();
            return 0;
        } catch (Exception ex) {
            LOG.debug("Describe-schema failed", ex);
            System.err.println("FAIL " + ex.getMessage());
            return 1;
        }
    }

    private Map<String, Object> describe(UmabootConfig config,
                                         SchemaIntrospectionService.Result source,
                                         SchemaModel schema) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaName", schema.schemaName());
        root.put("dbType", source.dbType());
        root.put("warnings", source.warnings());
        root.put("javaTypes", JavaTypeMapper.CURATED_OVERRIDE_TYPES);

        ArrayList<Map<String, Object>> tables = new ArrayList<>();
        schema.tables().stream()
                .filter(t -> includeJunctions || !t.junction())
                .sorted(Comparator.comparing(TableModel::name, String.CASE_INSENSITIVE_ORDER))
                .forEach(t -> tables.add(describeTable(config, t)));
        root.put("tables", tables);
        return root;
    }

    private Map<String, Object> describeTable(UmabootConfig config, TableModel table) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", table.name());
        out.put("schema", table.schema());
        out.put("comment", table.comment());
        out.put("junction", table.junction());
        out.put("primaryKey", table.primaryKey());
        out.put("defaultClassName", Naming.entityClass(
                table.name(),
                config.generation().tables().classNameStripPrefix()));

        ArrayList<Map<String, Object>> columns = new ArrayList<>();
        for (ColumnModel column : table.columns()) {
            columns.add(describeColumn(column));
        }
        out.put("columns", columns);
        return out;
    }

    private Map<String, Object> describeColumn(ColumnModel column) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", column.name());
        out.put("jdbcType", column.jdbcType());
        out.put("sqlType", column.sqlType());
        out.put("size", column.size());
        out.put("scale", column.scale());
        out.put("nullable", column.nullable());
        out.put("primaryKey", column.primaryKey());
        out.put("autoIncrement", column.autoIncrement());
        out.put("defaultValue", column.defaultValue());
        out.put("comment", column.comment());
        out.put("enumValues", column.enumValues());
        out.put("defaultJavaType", JavaTypeMapper.javaType(column));
        return out;
    }
}

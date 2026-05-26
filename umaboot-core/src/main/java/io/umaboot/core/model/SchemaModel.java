package io.umaboot.core.model;

import java.util.List;
import java.util.Objects;

/**
 * Top-level immutable representation of a database schema.
 *
 * <p>Produced by introspection ({@code core.introspection.*}); consumed by
 * generators ({@code core.generator.*}) and architecture renderers
 * ({@code core.architecture.*}).</p>
 *
 * @param schemaName logical schema name (e.g. {@code public} for Postgres)
 * @param tables     all tables in the schema, in introspection order
 */
public record SchemaModel(String schemaName, List<TableModel> tables) {

    public SchemaModel {
        Objects.requireNonNull(schemaName, "schemaName");
        Objects.requireNonNull(tables, "tables");
        tables = List.copyOf(tables);
    }

    /**
     * Find a table by name (case-insensitive).
     *
     * @param name table name
     * @return the table, or {@code null} if not found
     */
    public TableModel findTable(String name) {
        for (TableModel t : tables) {
            if (t.name().equalsIgnoreCase(name)) {
                return t;
            }
        }
        return null;
    }
}

package io.umaboot.core.model;

import java.util.List;
import java.util.Objects;

/**
 * A single database table.
 *
 * @param name              raw table name as reported by JDBC (e.g. {@code customer_orders})
 * @param schema            schema this table belongs to
 * @param comment           table comment / description, or empty string
 * @param columns           all columns in declared order
 * @param primaryKey        ordered list of column names that form the PK; empty if none
 * @param uniqueConstraints list of unique constraints (each is a list of column names)
 * @param relationships     relationships originating from this table; populated by RelationshipEngine
 * @param junction          true if this table is a pure junction/link table for a many-to-many
 */
public record TableModel(
        String name,
        String schema,
        String comment,
        List<ColumnModel> columns,
        List<String> primaryKey,
        List<List<String>> uniqueConstraints,
        List<RelationshipModel> relationships,
        boolean junction) {

    public TableModel {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(schema, "schema");
        comment = comment == null ? "" : comment;
        columns = List.copyOf(Objects.requireNonNull(columns, "columns"));
        primaryKey = List.copyOf(Objects.requireNonNull(primaryKey, "primaryKey"));
        uniqueConstraints = uniqueConstraints == null
                ? List.of()
                : uniqueConstraints.stream().map(List::copyOf).toList();
        relationships = relationships == null ? List.of() : List.copyOf(relationships);
    }

    /** Convenience: find a column by name (case-insensitive). */
    public ColumnModel findColumn(String columnName) {
        for (ColumnModel c : columns) {
            if (c.name().equalsIgnoreCase(columnName)) {
                return c;
            }
        }
        return null;
    }

    /** True if the primary key consists of exactly one column. */
    public boolean hasSimplePrimaryKey() {
        return primaryKey.size() == 1;
    }

    /** Returns a copy of this table with a new relationships list. */
    public TableModel withRelationships(List<RelationshipModel> newRelationships) {
        return new TableModel(name, schema, comment, columns, primaryKey,
                uniqueConstraints, newRelationships, junction);
    }

    /** Returns a copy of this table flagged as junction (or not). */
    public TableModel withJunction(boolean isJunction) {
        return new TableModel(name, schema, comment, columns, primaryKey,
                uniqueConstraints, relationships, isJunction);
    }
}

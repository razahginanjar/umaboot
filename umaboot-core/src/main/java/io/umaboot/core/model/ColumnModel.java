package io.umaboot.core.model;

import java.util.Objects;

/**
 * A single column in a table.
 *
 * @param name           raw column name (e.g. {@code first_name})
 * @param jdbcType       {@code java.sql.Types} constant value
 * @param sqlType        DB-specific SQL type string (e.g. {@code varchar}, {@code uuid}, {@code int8})
 * @param size           character length for strings, precision for numerics; {@code 0} if not applicable
 * @param scale          scale for numerics; {@code 0} if not applicable
 * @param nullable       true if the column accepts NULL
 * @param primaryKey     true if this column participates in the primary key
 * @param autoIncrement  true if this is an identity / serial column
 * @param defaultValue   raw default value expression, or {@code null} if none
 * @param comment        column comment, or empty string
 * @param enumValues     allowed enum values if this column is backed by a Postgres ENUM, otherwise empty
 */
public record ColumnModel(
        String name,
        int jdbcType,
        String sqlType,
        int size,
        int scale,
        boolean nullable,
        boolean primaryKey,
        boolean autoIncrement,
        String defaultValue,
        String comment,
        java.util.List<String> enumValues) {

    public ColumnModel {
        Objects.requireNonNull(name, "name");
        sqlType = sqlType == null ? "" : sqlType;
        comment = comment == null ? "" : comment;
        enumValues = enumValues == null ? java.util.List.of() : java.util.List.copyOf(enumValues);
    }

    /** True if this column is backed by a database ENUM type. */
    public boolean isEnum() {
        return !enumValues.isEmpty();
    }
}

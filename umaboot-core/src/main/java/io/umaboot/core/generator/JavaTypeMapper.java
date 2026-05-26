package io.umaboot.core.generator;

import io.umaboot.core.model.ColumnModel;

import java.sql.Types;

/**
 * Maps SQL/JDBC types to Java types for entity field generation.
 */
public final class JavaTypeMapper {

    /**
     * Curated list of Java types the per-column override dropdown offers.
     * Stored as the canonical type string the templates render verbatim:
     * primitives, java.lang.* short names (no import needed), or fully-qualified
     * names (the existing import resolver handles them).
     *
     * <p>Each entry maps to one display name in the panel dropdown via
     * {@link #simpleName(String)}.</p>
     */
    public static final java.util.List<String> CURATED_OVERRIDE_TYPES = java.util.List.of(
            "int", "long", "short", "byte", "boolean", "float", "double",
            "Integer", "Long", "Short", "Byte", "Boolean", "Float", "Double",
            "String", "Object",
            "java.math.BigDecimal",
            "java.math.BigInteger",
            "java.time.LocalDate",
            "java.time.LocalDateTime",
            "java.time.OffsetDateTime",
            "java.time.Instant",
            "java.time.LocalTime",
            "java.util.UUID",
            "java.util.Map<String,Object>",
            "java.util.List<Object>",
            "byte[]"
    );

    private JavaTypeMapper() {}

    /**
     * Returns the Java type name for the given column, honoring an explicit
     * per-column override when one is provided. The caller is responsible
     * for adding the corresponding import (use {@link #importFor(String)}).
     */
    public static String javaType(ColumnModel column, String overrideJavaType) {
        if (overrideJavaType != null && !overrideJavaType.isBlank()) {
            return overrideJavaType;
        }
        return javaType(column);
    }

    /**
     * Returns the simple Java type name for the given column.
     * The caller is responsible for adding the corresponding import.
     */
    public static String javaType(ColumnModel column) {
        // Postgres ENUMs render as plain Strings unless explicitly modeled — v0.1 uses String
        if (column.isEnum()) return "String";

        return switch (column.jdbcType()) {
            case Types.BIT, Types.BOOLEAN -> "Boolean";
            case Types.TINYINT, Types.SMALLINT -> "Short";
            case Types.INTEGER -> "Integer";
            case Types.BIGINT -> "Long";
            case Types.REAL, Types.FLOAT -> "Float";
            case Types.DOUBLE -> "Double";
            case Types.NUMERIC, Types.DECIMAL -> "java.math.BigDecimal";
            case Types.CHAR, Types.NCHAR, Types.VARCHAR, Types.NVARCHAR,
                 Types.LONGVARCHAR, Types.LONGNVARCHAR, Types.CLOB, Types.NCLOB -> "String";
            case Types.DATE -> "java.time.LocalDate";
            case Types.TIME -> "java.time.LocalTime";
            case Types.TIMESTAMP -> "java.time.LocalDateTime";
            case Types.TIME_WITH_TIMEZONE -> "java.time.OffsetTime";
            case Types.TIMESTAMP_WITH_TIMEZONE -> "java.time.OffsetDateTime";
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> "byte[]";
            case Types.OTHER -> {
                // Postgres-specific: uuid, jsonb, etc.
                String t = column.sqlType().toLowerCase();
                if (t.equals("uuid")) yield "java.util.UUID";
                if (t.contains("json")) yield "String";
                yield "String";
            }
            case Types.ARRAY -> "Object[]";
            default -> "Object";
        };
    }

    /**
     * Returns the import statement (or empty string for primitive / lang types).
     */
    public static String importFor(String javaType) {
        if (javaType == null || javaType.isEmpty()) return "";
        if (!javaType.contains(".")) return ""; // primitives / java.lang
        return javaType;
    }

    /** Strip package prefix for use in type position. */
    public static String simpleName(String javaType) {
        int dot = javaType.lastIndexOf('.');
        return dot < 0 ? javaType : javaType.substring(dot + 1);
    }
}

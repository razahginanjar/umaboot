package io.umaboot.core.introspection.sqlfile;

import java.sql.Types;
import java.util.Locale;
import java.util.Map;

/**
 * Maps SQL type names from a parsed {@code .sql} DDL file back to
 * {@link java.sql.Types} integer constants.
 *
 * <p>When introspecting a live database via JDBC {@link java.sql.DatabaseMetaData},
 * the JDBC driver returns the {@code DATA_TYPE} column directly as a
 * {@code java.sql.Types.*} int. When parsing a {@code .sql} file we don't have
 * that luxury — JSqlParser hands us the raw column-type string ({@code "VARCHAR"},
 * {@code "BIGINT"}, {@code "TIMESTAMP WITH TIME ZONE"}). This class does the
 * mapping for the dialects Umaboot supports as schema sources: Postgres, MySQL,
 * MariaDB.</p>
 *
 * <p>The map is intentionally generous on aliases (e.g. both {@code INT} and
 * {@code INTEGER} map to {@link Types#INTEGER}) and case-insensitive. Unknown
 * types fall through to {@link Types#OTHER} — code generation will surface the
 * raw {@code sqlType} string in {@link io.umaboot.core.model.ColumnModel}, so
 * downstream {@code JavaTypeMapper} can still produce a sensible Java type via
 * the same path it uses for unknown-vendor types from JDBC.</p>
 */
public final class SqlTypeMapper {

    private SqlTypeMapper() {}

    /** Canonical type-string → JDBC type int. Lookup is case-insensitive. */
    private static final Map<String, Integer> TYPES = Map.ofEntries(
            // Integer family
            Map.entry("smallint",        Types.SMALLINT),
            Map.entry("smallserial",     Types.SMALLINT),
            Map.entry("int2",            Types.SMALLINT),
            Map.entry("tinyint",         Types.TINYINT),
            Map.entry("mediumint",       Types.INTEGER),
            Map.entry("int",             Types.INTEGER),
            Map.entry("integer",         Types.INTEGER),
            Map.entry("int4",            Types.INTEGER),
            Map.entry("serial",          Types.INTEGER),
            Map.entry("bigint",          Types.BIGINT),
            Map.entry("int8",            Types.BIGINT),
            Map.entry("bigserial",       Types.BIGINT),

            // Floating point / decimal
            Map.entry("real",            Types.REAL),
            Map.entry("float4",          Types.REAL),
            Map.entry("float",           Types.FLOAT),
            Map.entry("double",          Types.DOUBLE),
            Map.entry("double precision", Types.DOUBLE),
            Map.entry("float8",          Types.DOUBLE),
            Map.entry("numeric",         Types.NUMERIC),
            Map.entry("decimal",         Types.DECIMAL),
            Map.entry("money",           Types.DECIMAL),

            // Boolean
            Map.entry("boolean",         Types.BOOLEAN),
            Map.entry("bool",            Types.BOOLEAN),
            Map.entry("bit",             Types.BIT),

            // Character / text
            Map.entry("char",            Types.CHAR),
            Map.entry("character",       Types.CHAR),
            Map.entry("bpchar",          Types.CHAR),
            Map.entry("varchar",         Types.VARCHAR),
            Map.entry("character varying", Types.VARCHAR),
            Map.entry("text",            Types.LONGVARCHAR),
            Map.entry("tinytext",        Types.VARCHAR),
            Map.entry("mediumtext",      Types.LONGVARCHAR),
            Map.entry("longtext",        Types.LONGVARCHAR),
            Map.entry("nvarchar",        Types.NVARCHAR),
            Map.entry("nchar",           Types.NCHAR),

            // Binary
            Map.entry("bytea",           Types.VARBINARY),
            Map.entry("binary",          Types.BINARY),
            Map.entry("varbinary",       Types.VARBINARY),
            Map.entry("blob",            Types.BLOB),
            Map.entry("tinyblob",        Types.VARBINARY),
            Map.entry("mediumblob",      Types.BLOB),
            Map.entry("longblob",        Types.BLOB),

            // Temporal
            Map.entry("date",            Types.DATE),
            Map.entry("time",            Types.TIME),
            Map.entry("timetz",          Types.TIME_WITH_TIMEZONE),
            Map.entry("time with time zone", Types.TIME_WITH_TIMEZONE),
            Map.entry("timestamp",       Types.TIMESTAMP),
            Map.entry("timestamptz",     Types.TIMESTAMP_WITH_TIMEZONE),
            Map.entry("timestamp with time zone", Types.TIMESTAMP_WITH_TIMEZONE),
            Map.entry("datetime",        Types.TIMESTAMP),
            Map.entry("year",            Types.DATE),

            // Postgres-specific (mapped to closest JDBC equivalent)
            Map.entry("uuid",            Types.OTHER),
            Map.entry("json",            Types.OTHER),
            Map.entry("jsonb",           Types.OTHER),
            Map.entry("xml",             Types.SQLXML),
            Map.entry("inet",            Types.OTHER),
            Map.entry("cidr",            Types.OTHER),
            Map.entry("macaddr",         Types.OTHER),
            Map.entry("interval",        Types.OTHER),
            Map.entry("tsvector",        Types.OTHER),

            // SQL Server / T-SQL types
            Map.entry("ntext",           Types.LONGNVARCHAR),
            Map.entry("datetime2",       Types.TIMESTAMP),
            Map.entry("datetimeoffset",  Types.TIMESTAMP_WITH_TIMEZONE),
            Map.entry("smalldatetime",   Types.TIMESTAMP),
            Map.entry("uniqueidentifier", Types.OTHER),  // SQL Server GUID (no clean JDBC equivalent)
            Map.entry("smallmoney",      Types.DECIMAL),
            Map.entry("image",           Types.LONGVARBINARY),
            Map.entry("rowversion",      Types.BINARY),
            Map.entry("sql_variant",     Types.OTHER),
            Map.entry("hierarchyid",     Types.OTHER),
            Map.entry("geography",       Types.OTHER),
            Map.entry("geometry",        Types.OTHER)
    );

    /**
     * Map a raw SQL type name to the matching {@link Types} integer.
     * Returns {@link Types#OTHER} for anything not in the table.
     *
     * @param rawTypeName the type as it appears in the DDL (e.g.
     *                    {@code "VARCHAR"}, {@code "BIGINT"},
     *                    {@code "TIMESTAMP WITH TIME ZONE"})
     */
    public static int toJdbcType(String rawTypeName) {
        if (rawTypeName == null) return Types.OTHER;
        String key = rawTypeName.trim().toLowerCase(Locale.ROOT);
        // Strip trailing length/precision: "varchar(255)" → "varchar", "numeric(10,2)" → "numeric"
        int paren = key.indexOf('(');
        if (paren > 0) {
            key = key.substring(0, paren).trim();
        }
        return TYPES.getOrDefault(key, Types.OTHER);
    }

    /**
     * Returns the canonical SQL type string for {@link io.umaboot.core.model.ColumnModel#sqlType()},
     * lowercased and stripped of length/precision arguments. This matches the
     * shape of {@code TYPE_NAME} that JDBC introspectors return.
     */
    public static String canonicalSqlType(String rawTypeName) {
        if (rawTypeName == null) return "";
        String s = rawTypeName.trim().toLowerCase(Locale.ROOT);
        int paren = s.indexOf('(');
        if (paren > 0) s = s.substring(0, paren).trim();
        return s;
    }

    /**
     * Extracts {@code (size)} or {@code (size,scale)} arguments from a type string.
     * Returns {@code [size, scale]}; either value is {@code 0} when absent.
     */
    public static int[] parseSizeAndScale(String rawTypeName) {
        if (rawTypeName == null) return new int[]{0, 0};
        int open = rawTypeName.indexOf('(');
        int close = rawTypeName.indexOf(')');
        if (open < 0 || close < 0 || close <= open + 1) return new int[]{0, 0};
        String inner = rawTypeName.substring(open + 1, close).trim();
        // For ENUM('a','b','c') etc., inner contains commas inside string literals — leave as 0,0.
        if (inner.contains("'") || inner.contains("\"")) return new int[]{0, 0};
        String[] parts = inner.split(",");
        int size = 0, scale = 0;
        try {
            if (parts.length >= 1) size = Integer.parseInt(parts[0].trim());
            if (parts.length >= 2) scale = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException ex) {
            return new int[]{0, 0};
        }
        return new int[]{size, scale};
    }

    /**
     * Returns true when this raw type is a database-native auto-increment shorthand
     * that implies an identity column (Postgres SERIAL family).
     */
    public static boolean isPostgresSerial(String rawTypeName) {
        String c = canonicalSqlType(rawTypeName);
        return "smallserial".equals(c) || "serial".equals(c) || "bigserial".equals(c);
    }
}

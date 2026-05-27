package io.umaboot.core.introspection.sqlserver;

import io.umaboot.core.introspection.Introspector;
import io.umaboot.core.model.ColumnModel;
import io.umaboot.core.model.RelationshipModel;
import io.umaboot.core.model.RelationshipType;
import io.umaboot.core.model.SchemaModel;
import io.umaboot.core.model.TableModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Microsoft SQL Server introspector built on JDBC {@link DatabaseMetaData}.
 *
 * <p>Reads tables, columns, primary keys, foreign keys, and unique constraints
 * for a given schema (defaults to {@code dbo} when not supplied). Returns raw
 * owning-side relationships only — inverse-side resolution and junction-table
 * detection are handled by the {@code RelationshipEngine} downstream.</p>
 *
 * <h2>What's not (yet) surfaced</h2>
 * <ul>
 *   <li><b>ENUMs</b> — SQL Server doesn't have a first-class ENUM type. Teams
 *       typically model these with {@code CHECK} constraints or lookup tables.
 *       We don't lift {@code CHECK (col IN ('a','b'))} into Java enums in v1
 *       (would require parsing each constraint expression).</li>
 *   <li><b>Comments</b> — SQL Server stores table/column descriptions as
 *       <em>extended properties</em> via {@code sys.fn_listextendedproperty},
 *       which require an additional roundtrip per object. v1 leaves comments
 *       empty; OpenAPI descriptions and entity JavaDoc fall back to derived
 *       text from the column name.</li>
 *   <li><b>IDENTITY columns</b> — JDBC {@code DatabaseMetaData.getColumns()}
 *       reports {@code IS_AUTOINCREMENT='YES'} for these, so they're picked up
 *       automatically without any T-SQL-specific query.</li>
 * </ul>
 *
 * <p>The shape of this introspector deliberately mirrors {@code PostgresIntrospector}
 * — both are schema-based, both use JDBC standard methods. The only structural
 * difference is the absence of dialect-specific helper queries (Postgres ENUM /
 * pg_description); SQL Server's equivalents either don't exist (ENUMs) or are
 * out of scope for v1 (extended properties).</p>
 */
public final class SqlServerIntrospector implements Introspector {

    private static final Logger LOG = LoggerFactory.getLogger(SqlServerIntrospector.class);

    private final Connection connection;

    public SqlServerIntrospector(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    @Override
    public SchemaModel introspect(String schema) throws SQLException {
        Objects.requireNonNull(schema, "schema");
        requireSchemaExists(schema);
        DatabaseMetaData md = connection.getMetaData();

        List<TableModel> tables = new ArrayList<>();
        try (ResultSet tablesRs = md.getTables(null, schema, "%", new String[]{"TABLE"})) {
            while (tablesRs.next()) {
                String tableName = tablesRs.getString("TABLE_NAME");
                String tableSchema = tablesRs.getString("TABLE_SCHEM");
                LOG.debug("Introspecting table [{}].[{}]", tableSchema, tableName);

                List<String> pk = readPrimaryKey(md, tableSchema, tableName);
                Set<String> pkSet = new LinkedHashSet<>(pk);

                List<ColumnModel> columns = readColumns(md, tableSchema, tableName, pkSet);
                List<List<String>> unique = readUniqueConstraints(md, tableSchema, tableName, pk);
                List<RelationshipModel> rels = readForeignKeys(md, tableSchema, tableName, unique);

                tables.add(new TableModel(
                        tableName, tableSchema, "",   // empty comment in v1
                        columns, pk, unique, rels, false));
            }
        }
        return new SchemaModel(schema, tables);
    }

    /**
     * Verifies that the named schema exists in the current database before walking it.
     * Without this, a typo in the Schema field silently produces a {@code SchemaModel}
     * with zero tables — indistinguishable from a real empty schema.
     *
     * <p>Uses {@code INFORMATION_SCHEMA.SCHEMATA} which is part of the SQL standard
     * and available on every SQL Server install.</p>
     */
    private void requireSchemaExists(String schema) throws SQLException {
        String sql = "SELECT 1 FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException(
                            "Schema '" + schema + "' does not exist in this SQL Server database. "
                                    + "The default schema is 'dbo'; verify the Schema field.");
                }
            }
        }
    }

    // ------------------------------------------------------------------ tables / columns

    private List<String> readPrimaryKey(DatabaseMetaData md, String schema, String table) throws SQLException {
        Map<Short, String> ordered = new HashMap<>();
        try (ResultSet rs = md.getPrimaryKeys(null, schema, table)) {
            while (rs.next()) {
                ordered.put(rs.getShort("KEY_SEQ"), rs.getString("COLUMN_NAME"));
            }
        }
        return ordered.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList();
    }

    private List<ColumnModel> readColumns(DatabaseMetaData md, String schema, String table,
                                          Set<String> pkColumns) throws SQLException {
        List<ColumnModel> cols = new ArrayList<>();
        try (ResultSet rs = md.getColumns(null, schema, table, "%")) {
            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                int jdbcType = rs.getInt("DATA_TYPE");
                String sqlType = rs.getString("TYPE_NAME");
                int size = rs.getInt("COLUMN_SIZE");
                int scale = rs.getInt("DECIMAL_DIGITS");
                boolean nullable = rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
                String autoInc = rs.getString("IS_AUTOINCREMENT");
                boolean autoIncrement = "YES".equalsIgnoreCase(autoInc);
                String def = rs.getString("COLUMN_DEF");

                // Normalize SQL Server type names: the JDBC driver may return
                // " identity" suffixes for IDENTITY columns and "(MAX)" suffixes
                // for nvarchar(max) etc. Strip both for a clean canonical type.
                if (sqlType != null) {
                    sqlType = sqlType.toLowerCase(Locale.ROOT).replaceAll(" identity", "").trim();
                }

                cols.add(new ColumnModel(
                        name, jdbcType, sqlType, size, scale, nullable,
                        pkColumns.contains(name), autoIncrement, def, "", List.of()));
            }
        }
        return cols;
    }

    private List<List<String>> readUniqueConstraints(DatabaseMetaData md, String schema, String table,
                                                     List<String> primaryKey) throws SQLException {
        Map<String, Map<Short, String>> grouped = new LinkedHashMap<>();
        try (ResultSet rs = md.getIndexInfo(null, schema, table, true, false)) {
            while (rs.next()) {
                boolean nonUnique = rs.getBoolean("NON_UNIQUE");
                if (nonUnique) continue;
                String idxName = rs.getString("INDEX_NAME");
                String column = rs.getString("COLUMN_NAME");
                short pos = rs.getShort("ORDINAL_POSITION");
                if (idxName == null || column == null) continue;
                grouped.computeIfAbsent(idxName, k -> new HashMap<>()).put(pos, column);
            }
        }
        List<List<String>> result = new ArrayList<>();
        Set<String> pkSet = new LinkedHashSet<>(primaryKey);
        for (Map<Short, String> idx : grouped.values()) {
            List<String> cols = idx.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(Map.Entry::getValue)
                    .toList();
            // Skip if this unique index is exactly the primary key
            if (cols.size() == pkSet.size() && pkSet.containsAll(cols)) continue;
            result.add(cols);
        }
        return result;
    }

    private List<RelationshipModel> readForeignKeys(DatabaseMetaData md, String schema, String table,
                                                    List<List<String>> uniqueConstraints) throws SQLException {
        Map<String, List<String[]>> fkGroups = new LinkedHashMap<>();
        Map<String, String> fkToTable = new HashMap<>();
        try (ResultSet rs = md.getImportedKeys(null, schema, table)) {
            while (rs.next()) {
                String fkName = rs.getString("FK_NAME");
                String fkColumn = rs.getString("FKCOLUMN_NAME");
                String pkTable = rs.getString("PKTABLE_NAME");
                String pkColumn = rs.getString("PKCOLUMN_NAME");
                short keySeq = rs.getShort("KEY_SEQ");
                if (fkName == null) {
                    fkName = "fk_" + table + "_" + fkColumn;
                }
                fkToTable.put(fkName, pkTable);
                fkGroups.computeIfAbsent(fkName, k -> new ArrayList<>())
                        .add(new String[]{String.valueOf(keySeq), fkColumn, pkColumn});
            }
        }

        List<RelationshipModel> rels = new ArrayList<>();
        for (var entry : fkGroups.entrySet()) {
            String fkName = entry.getKey();
            List<String[]> rows = entry.getValue();
            rows.sort((a, b) -> Short.compare(Short.parseShort(a[0]), Short.parseShort(b[0])));
            List<String> fromCols = rows.stream().map(r -> r[1]).toList();
            List<String> toCols = rows.stream().map(r -> r[2]).toList();
            String pkTable = fkToTable.get(fkName);

            boolean isOneToOne = uniqueConstraints.stream()
                    .anyMatch(uc -> uc.size() == fromCols.size() && uc.containsAll(fromCols));
            RelationshipType type = isOneToOne
                    ? new RelationshipType.OneToOne()
                    : new RelationshipType.ManyToOne();

            String fieldName = deriveFieldName(fromCols, pkTable);
            boolean selfRef = pkTable.equalsIgnoreCase(table);

            rels.add(new RelationshipModel(table, pkTable, type, fromCols, toCols,
                    fieldName, true, selfRef));
        }
        return rels;
    }

    private static String deriveFieldName(List<String> fromColumns, String referencedTable) {
        if (fromColumns.size() == 1) {
            String col = fromColumns.get(0);
            String lower = col.toLowerCase();
            if (lower.endsWith("_id") && lower.length() > 3) {
                return lower.substring(0, lower.length() - 3);
            }
        }
        return referencedTable.toLowerCase();
    }
}

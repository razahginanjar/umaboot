package io.umaboot.core.introspection.mysql;

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
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * MySQL-specific introspector.
 *
 * <p>MySQL exposes a more reliable view of metadata via {@code information_schema}
 * than {@link DatabaseMetaData}, particularly for ENUM columns, generated
 * columns, and column comments. This introspector uses INFORMATION_SCHEMA
 * directly for those and falls back to the JDBC API for the rest.</p>
 *
 * <p>The {@code schema} argument here is interpreted as the MySQL <em>database</em>
 * name (since MySQL conflates the two).</p>
 */
public final class MysqlIntrospector implements Introspector {

    private static final Logger LOG = LoggerFactory.getLogger(MysqlIntrospector.class);

    private final Connection connection;

    public MysqlIntrospector(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    @Override
    public SchemaModel introspect(String schema) throws SQLException {
        Objects.requireNonNull(schema, "schema");
        requireSchemaExists(schema);
        DatabaseMetaData md = connection.getMetaData();

        Map<String, String> tableComments = loadTableComments(schema);
        // (table.column) -> comment
        Map<String, String> columnComments = new LinkedHashMap<>();
        // (table.column) -> ENUM('a','b','c')
        Map<String, List<String>> enumColumns = loadEnumValues(schema, columnComments);

        List<TableModel> tables = new ArrayList<>();
        try (ResultSet tablesRs = md.getTables(schema, null, "%", new String[]{"TABLE"})) {
            while (tablesRs.next()) {
                String tableName = tablesRs.getString("TABLE_NAME");
                LOG.debug("Introspecting table {}.{}", schema, tableName);

                List<String> pk = readPrimaryKey(md, schema, tableName);
                Set<String> pkSet = new LinkedHashSet<>(pk);

                List<ColumnModel> columns = readColumns(md, schema, tableName, pkSet,
                        columnComments, enumColumns);
                List<List<String>> unique = readUniqueConstraints(md, schema, tableName, pk);
                List<RelationshipModel> rels = readForeignKeys(md, schema, tableName, unique);

                String tableComment = tableComments.getOrDefault(tableName, "");
                tables.add(new TableModel(
                        tableName, schema, tableComment,
                        columns, pk, unique, rels, false));
            }
        }
        return new SchemaModel(schema, tables);
    }

    /**
     * Verifies the named MySQL database (a.k.a. schema in INFORMATION_SCHEMA terms)
     * actually exists on the connected server before we walk it. Without this, a
     * typo in the Database field silently produces a {@code SchemaModel} with
     * zero tables — indistinguishable from a real empty database.
     */
    private void requireSchemaExists(String schema) throws SQLException {
        String sql = "SELECT 1 FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException(
                            "Database '" + schema + "' does not exist on this MySQL server. "
                                    + "Verify the Database field matches an actual database name "
                                    + "visible to the connected user.");
                }
            }
        }
    }

    // ------------------------------------------------------------------ tables / columns

    private List<String> readPrimaryKey(DatabaseMetaData md, String schema, String table) throws SQLException {
        Map<Short, String> ordered = new HashMap<>();
        try (ResultSet rs = md.getPrimaryKeys(schema, null, table)) {
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
                                          Set<String> pkColumns,
                                          Map<String, String> columnComments,
                                          Map<String, List<String>> enumColumns) throws SQLException {
        List<ColumnModel> cols = new ArrayList<>();
        try (ResultSet rs = md.getColumns(schema, null, table, "%")) {
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
                String key = table + "." + name;
                String comment = columnComments.getOrDefault(key, "");
                List<String> enumVals = enumColumns.getOrDefault(key, List.of());

                cols.add(new ColumnModel(
                        name, jdbcType, sqlType, size, scale, nullable,
                        pkColumns.contains(name), autoIncrement, def, comment, enumVals));
            }
        }
        return cols;
    }

    private List<List<String>> readUniqueConstraints(DatabaseMetaData md, String schema, String table,
                                                     List<String> primaryKey) throws SQLException {
        Map<String, Map<Short, String>> grouped = new LinkedHashMap<>();
        try (ResultSet rs = md.getIndexInfo(schema, null, table, true, false)) {
            while (rs.next()) {
                if (rs.getBoolean("NON_UNIQUE")) continue;
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
            // Skip the primary index (called "PRIMARY" in MySQL)
            if (cols.size() == pkSet.size() && pkSet.containsAll(cols)) continue;
            result.add(cols);
        }
        return result;
    }

    private List<RelationshipModel> readForeignKeys(DatabaseMetaData md, String schema, String table,
                                                    List<List<String>> uniqueConstraints) throws SQLException {
        Map<String, List<String[]>> fkGroups = new LinkedHashMap<>();
        Map<String, String> fkToTable = new HashMap<>();
        try (ResultSet rs = md.getImportedKeys(schema, null, table)) {
            while (rs.next()) {
                String fkName = rs.getString("FK_NAME");
                String fkColumn = rs.getString("FKCOLUMN_NAME");
                String pkTable = rs.getString("PKTABLE_NAME");
                String pkColumn = rs.getString("PKCOLUMN_NAME");
                short keySeq = rs.getShort("KEY_SEQ");
                if (fkName == null) fkName = "fk_" + table + "_" + fkColumn;
                fkToTable.put(fkName, pkTable);
                fkGroups.computeIfAbsent(fkName, k -> new ArrayList<>())
                        .add(new String[]{String.valueOf(keySeq), fkColumn, pkColumn});
            }
        }

        List<RelationshipModel> rels = new ArrayList<>();
        for (var entry : fkGroups.entrySet()) {
            List<String[]> rows = entry.getValue();
            rows.sort((a, b) -> Short.compare(Short.parseShort(a[0]), Short.parseShort(b[0])));
            List<String> fromCols = rows.stream().map(r -> r[1]).toList();
            List<String> toCols = rows.stream().map(r -> r[2]).toList();
            String pkTable = fkToTable.get(entry.getKey());

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

    // ------------------------------------------------------------------ MySQL-specific helpers

    private Map<String, String> loadTableComments(String schema) {
        String sql = """
                SELECT TABLE_NAME, TABLE_COMMENT
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = ?
                """;
        Map<String, String> result = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String c = rs.getString(2);
                    if (c != null && !c.isEmpty()) result.put(rs.getString(1), c);
                }
            }
        } catch (SQLException ex) {
            LOG.debug("Failed to read MySQL table comments: {}", ex.getMessage());
        }
        return result;
    }

    /**
     * Reads ENUM(...) column types and column comments in a single pass.
     * Populates {@code columnCommentsOut} and returns the enum-values map.
     */
    private Map<String, List<String>> loadEnumValues(String schema,
                                                     Map<String, String> columnCommentsOut) {
        String sql = """
                SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = ?
                """;
        Map<String, List<String>> enumMap = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String table = rs.getString(1);
                    String column = rs.getString(2);
                    String columnType = rs.getString(3);
                    String comment = rs.getString(4);

                    String key = table + "." + column;
                    if (comment != null && !comment.isEmpty()) {
                        columnCommentsOut.put(key, comment);
                    }
                    if (columnType != null && columnType.toLowerCase().startsWith("enum(")) {
                        enumMap.put(key, parseEnumLiteral(columnType));
                    }
                }
            }
        } catch (SQLException ex) {
            LOG.debug("Failed to read MySQL ENUM metadata: {}", ex.getMessage());
        }
        return enumMap;
    }

    /**
     * Parses {@code enum('A','B','C')} into {@code [A, B, C]}.
     */
    static List<String> parseEnumLiteral(String enumType) {
        int open = enumType.indexOf('(');
        int close = enumType.lastIndexOf(')');
        if (open < 0 || close <= open) return List.of();
        String inside = enumType.substring(open + 1, close);
        List<String> values = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < inside.length(); i++) {
            char c = inside.charAt(i);
            if (c == '\'') {
                // handle escaped '' inside quotes
                if (inQuotes && i + 1 < inside.length() && inside.charAt(i + 1) == '\'') {
                    cur.append('\'');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                values.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        if (!cur.isEmpty()) values.add(cur.toString());
        return values;
    }
}

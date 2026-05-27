package io.umaboot.core.introspection.sqlite;

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
 * SQLite introspector built on JDBC {@link DatabaseMetaData} via the
 * {@code org.xerial:sqlite-jdbc} driver.
 *
 * <p>SQLite has no schema concept beyond the {@code main} ATTACH alias, so we
 * pass {@code null} for both catalog and schema in metadata calls. The
 * {@code schemaName} argument is treated as a logical label only — it ends up
 * on the returned {@link SchemaModel#schemaName()} so downstream templates
 * have a sensible value, but doesn't filter anything.</p>
 *
 * <h2>What's not surfaced</h2>
 * <ul>
 *   <li><b>ENUMs</b> — SQLite has no first-class enum type. Teams typically
 *       use {@code CHECK (col IN ('a','b'))} or lookup tables. We don't lift
 *       these into Java enums in v1.</li>
 *   <li><b>Comments</b> — SQLite has no native COMMENT ON / inline COMMENT
 *       support; comments stay empty.</li>
 *   <li><b>Type affinity nuance</b> — SQLite's dynamic typing means the JDBC
 *       driver's reported {@code DATA_TYPE} can vary based on stored values.
 *       For codegen we trust the column-type declaration in the schema, which
 *       the {@link io.umaboot.core.introspection.sqlfile.SqlTypeMapper} maps
 *       via the same affinity-aware table. {@code INTEGER PRIMARY KEY}
 *       columns are reported as {@code IS_AUTOINCREMENT='YES'} by xerial.</li>
 * </ul>
 *
 * <p>Internal SQLite tables (those starting with {@code sqlite_}) are skipped
 * — they're virtual / FTS / sequence machinery, not user tables.</p>
 */
public final class SqliteIntrospector implements Introspector {

    private static final Logger LOG = LoggerFactory.getLogger(SqliteIntrospector.class);

    private final Connection connection;

    public SqliteIntrospector(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    @Override
    public SchemaModel introspect(String schema) throws SQLException {
        // schema is a logical label — not used by SQLite. Default to "main" so
        // generated YAML remains non-empty.
        String label = (schema == null || schema.isBlank()) ? "main" : schema;
        DatabaseMetaData md = connection.getMetaData();

        List<TableModel> tables = new ArrayList<>();
        try (ResultSet tablesRs = md.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (tablesRs.next()) {
                String tableName = tablesRs.getString("TABLE_NAME");
                if (tableName == null || tableName.toLowerCase(Locale.ROOT).startsWith("sqlite_")) {
                    continue; // skip internal sqlite_master / sqlite_sequence
                }
                LOG.debug("Introspecting SQLite table {}", tableName);

                List<String> pk = readPrimaryKey(md, tableName);
                Set<String> pkSet = new LinkedHashSet<>(pk);

                List<ColumnModel> columns = readColumns(md, tableName, pkSet);
                List<List<String>> unique = readUniqueConstraints(md, tableName, pk);
                List<RelationshipModel> rels = readForeignKeys(md, tableName, unique);

                tables.add(new TableModel(
                        tableName, label, "",   // SQLite has no native comments
                        columns, pk, unique, rels, false));
            }
        }
        return new SchemaModel(label, tables);
    }

    // ------------------------------------------------------------------ tables / columns

    private List<String> readPrimaryKey(DatabaseMetaData md, String table) throws SQLException {
        Map<Short, String> ordered = new HashMap<>();
        try (ResultSet rs = md.getPrimaryKeys(null, null, table)) {
            while (rs.next()) {
                ordered.put(rs.getShort("KEY_SEQ"), rs.getString("COLUMN_NAME"));
            }
        }
        return ordered.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList();
    }

    private List<ColumnModel> readColumns(DatabaseMetaData md, String table,
                                          Set<String> pkColumns) throws SQLException {
        List<ColumnModel> cols = new ArrayList<>();
        try (ResultSet rs = md.getColumns(null, null, table, "%")) {
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

                // Lowercase the type name for parity with the other introspectors.
                if (sqlType != null) sqlType = sqlType.toLowerCase(Locale.ROOT).trim();

                // SQLite affinity rule: a single-column INTEGER PRIMARY KEY is
                // an alias for the implicit rowid and auto-increments, even
                // without the AUTOINCREMENT keyword. Some xerial driver
                // versions don't surface this via IS_AUTOINCREMENT, so detect
                // it ourselves as a safety net.
                if (!autoIncrement
                        && pkColumns.size() == 1
                        && pkColumns.contains(name)
                        && "integer".equals(sqlType)) {
                    autoIncrement = true;
                }

                cols.add(new ColumnModel(
                        name, jdbcType, sqlType, size, scale, nullable,
                        pkColumns.contains(name), autoIncrement, def, "", List.of()));
            }
        }
        return cols;
    }

    private List<List<String>> readUniqueConstraints(DatabaseMetaData md, String table,
                                                     List<String> primaryKey) throws SQLException {
        Map<String, Map<Short, String>> grouped = new LinkedHashMap<>();
        try (ResultSet rs = md.getIndexInfo(null, null, table, true, false)) {
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

    private List<RelationshipModel> readForeignKeys(DatabaseMetaData md, String table,
                                                    List<List<String>> uniqueConstraints) throws SQLException {
        Map<String, List<String[]>> fkGroups = new LinkedHashMap<>();
        Map<String, String> fkToTable = new HashMap<>();
        try (ResultSet rs = md.getImportedKeys(null, null, table)) {
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

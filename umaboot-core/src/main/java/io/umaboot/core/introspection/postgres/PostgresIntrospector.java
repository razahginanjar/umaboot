package io.umaboot.core.introspection.postgres;

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
 * PostgreSQL-specific introspector built on JDBC {@link DatabaseMetaData}.
 *
 * <p>Reads tables, columns, primary keys, foreign keys, unique constraints, and
 * Postgres ENUM type values. Returns raw owning-side relationships only —
 * inverse-side ManyToOne→OneToMany resolution and junction-table detection are
 * handled by the {@code RelationshipEngine} downstream.</p>
 */
public final class PostgresIntrospector implements Introspector {

    private static final Logger LOG = LoggerFactory.getLogger(PostgresIntrospector.class);

    private final Connection connection;

    public PostgresIntrospector(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    @Override
    public SchemaModel introspect(String schema) throws SQLException {
        Objects.requireNonNull(schema, "schema");
        requireSchemaExists(schema);
        DatabaseMetaData md = connection.getMetaData();

        // Postgres ENUM types: typname -> values
        Map<String, List<String>> enumTypes = loadEnumTypes(schema);

        // Map column-comment lookup: "table.column" -> comment
        Map<String, String> columnComments = loadColumnComments(schema);
        // Table comments
        Map<String, String> tableComments = loadTableComments(schema);
        // Map column -> Postgres ENUM type name
        Map<String, String> columnEnumTypes = loadColumnEnumTypes(schema);

        List<TableModel> tables = new ArrayList<>();
        try (ResultSet tablesRs = md.getTables(null, schema, "%", new String[]{"TABLE"})) {
            while (tablesRs.next()) {
                String tableName = tablesRs.getString("TABLE_NAME");
                String tableSchema = tablesRs.getString("TABLE_SCHEM");
                LOG.debug("Introspecting table {}.{}", tableSchema, tableName);

                List<String> pk = readPrimaryKey(md, tableSchema, tableName);
                Set<String> pkSet = new LinkedHashSet<>(pk);

                List<ColumnModel> columns = readColumns(md, tableSchema, tableName, pkSet,
                        columnComments, columnEnumTypes, enumTypes);

                List<List<String>> unique = readUniqueConstraints(md, tableSchema, tableName, pk);
                List<RelationshipModel> rels = readForeignKeys(md, tableSchema, tableName, unique);

                String tableComment = tableComments.getOrDefault(tableName, "");
                tables.add(new TableModel(
                        tableName, tableSchema, tableComment,
                        columns, pk, unique, rels, false));
            }
        }
        return new SchemaModel(schema, tables);
    }

    /**
     * Verifies that the named schema actually exists on the connected server before
     * we walk it. Without this, a typo in the Schema field silently produces a
     * {@code SchemaModel} with zero tables — indistinguishable from a real
     * empty schema.
     *
     * <p>The query targets {@code information_schema.schemata} which is
     * world-readable on a default Postgres install; if the user's account
     * somehow can't read it, the query throws and we let the {@link SQLException}
     * propagate — that's a clearer error than an empty result anyway.</p>
     */
    private void requireSchemaExists(String schema) throws SQLException {
        String sql = "SELECT 1 FROM information_schema.schemata WHERE schema_name = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException(
                            "Schema '" + schema + "' does not exist on this PostgreSQL server. "
                                    + "Verify the Schema field — common values are 'public', 'app', "
                                    + "or your project's schema name.");
                }
            }
        }
    }

    // ------------------------------------------------------------------ tables / columns

    private List<String> readPrimaryKey(DatabaseMetaData md, String schema, String table) throws SQLException {
        // Use TreeMap-by-KEY_SEQ to preserve composite key ordering
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
                                          Set<String> pkColumns,
                                          Map<String, String> columnComments,
                                          Map<String, String> columnEnumTypes,
                                          Map<String, List<String>> enumTypes) throws SQLException {
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
                String comment = columnComments.getOrDefault(table + "." + name, "");

                String enumTypeName = columnEnumTypes.get(table + "." + name);
                List<String> enumValues = enumTypeName == null
                        ? List.of()
                        : enumTypes.getOrDefault(enumTypeName, List.of());

                cols.add(new ColumnModel(
                        name, jdbcType, sqlType, size, scale, nullable,
                        pkColumns.contains(name), autoIncrement, def, comment, enumValues));
            }
        }
        return cols;
    }

    private List<List<String>> readUniqueConstraints(DatabaseMetaData md, String schema, String table,
                                                     List<String> primaryKey) throws SQLException {
        // INDEX_NAME -> ordered columns
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
        // Group composite FKs by FK_NAME
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

            // OneToOne if FK columns form a unique constraint
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
        // If single FK column ends with _id, strip it -> e.g. customer_id -> customer
        if (fromColumns.size() == 1) {
            String col = fromColumns.get(0);
            String lower = col.toLowerCase();
            if (lower.endsWith("_id") && lower.length() > 3) {
                return lower.substring(0, lower.length() - 3);
            }
        }
        return referencedTable.toLowerCase();
    }

    // ------------------------------------------------------------------ Postgres-specific helpers

    private Map<String, List<String>> loadEnumTypes(String schema) throws SQLException {
        String sql = """
                SELECT t.typname, e.enumlabel
                FROM pg_type t
                JOIN pg_enum e ON t.oid = e.enumtypid
                JOIN pg_catalog.pg_namespace n ON n.oid = t.typnamespace
                WHERE n.nspname = ?
                ORDER BY t.typname, e.enumsortorder
                """;
        Map<String, List<String>> result = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.computeIfAbsent(rs.getString(1), k -> new ArrayList<>())
                            .add(rs.getString(2));
                }
            }
        } catch (SQLException ex) {
            // Tolerate absence of pg_enum on non-PG dialects in shared tests
            LOG.debug("pg_enum lookup failed: {}", ex.getMessage());
        }
        return result;
    }

    private Map<String, String> loadColumnEnumTypes(String schema) throws SQLException {
        String sql = """
                SELECT c.table_name, c.column_name, c.udt_name
                FROM information_schema.columns c
                JOIN pg_type t ON t.typname = c.udt_name
                WHERE c.table_schema = ?
                  AND t.typtype = 'e'
                """;
        Map<String, String> result = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString(1) + "." + rs.getString(2), rs.getString(3));
                }
            }
        } catch (SQLException ex) {
            LOG.debug("information_schema enum lookup failed: {}", ex.getMessage());
        }
        return result;
    }

    private Map<String, String> loadColumnComments(String schema) {
        String sql = """
                SELECT c.table_name, c.column_name, pgd.description
                FROM information_schema.columns c
                LEFT JOIN pg_catalog.pg_statio_all_tables st
                  ON st.schemaname = c.table_schema AND st.relname = c.table_name
                LEFT JOIN pg_catalog.pg_description pgd
                  ON pgd.objoid = st.relid AND pgd.objsubid = c.ordinal_position
                WHERE c.table_schema = ?
                """;
        Map<String, String> result = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String d = rs.getString(3);
                    if (d != null && !d.isEmpty()) {
                        result.put(rs.getString(1) + "." + rs.getString(2), d);
                    }
                }
            }
        } catch (SQLException ex) {
            LOG.debug("Column comment lookup failed: {}", ex.getMessage());
        }
        return result;
    }

    private Map<String, String> loadTableComments(String schema) {
        String sql = """
                SELECT c.relname, pgd.description
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                LEFT JOIN pg_description pgd ON pgd.objoid = c.oid AND pgd.objsubid = 0
                WHERE n.nspname = ? AND c.relkind = 'r'
                """;
        Map<String, String> result = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String d = rs.getString(2);
                    if (d != null && !d.isEmpty()) result.put(rs.getString(1), d);
                }
            }
        } catch (SQLException ex) {
            LOG.debug("Table comment lookup failed: {}", ex.getMessage());
        }
        return result;
    }
}

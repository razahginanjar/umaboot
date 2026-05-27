package io.umaboot.core.introspection.sqlfile;

import io.umaboot.core.introspection.Introspector;
import io.umaboot.core.model.ColumnModel;
import io.umaboot.core.model.RelationshipModel;
import io.umaboot.core.model.RelationshipType;
import io.umaboot.core.model.SchemaModel;
import io.umaboot.core.model.TableModel;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.alter.AlterExpression;
import net.sf.jsqlparser.statement.comment.Comment;
import net.sf.jsqlparser.statement.create.table.ColumnDefinition;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.create.table.ForeignKeyIndex;
import net.sf.jsqlparser.statement.create.table.Index;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a {@code .sql} DDL file and produces the same {@link SchemaModel} shape
 * that the live-DB introspectors ({@link io.umaboot.core.introspection.postgres.PostgresIntrospector PostgresIntrospector},
 * {@link io.umaboot.core.introspection.mysql.MysqlIntrospector MysqlIntrospector}) would.
 *
 * <p>This is the no-live-DB code path: the user gives us a checked-in
 * {@code schema.sql} and we generate a Spring Boot project from it without
 * needing Docker, a running Postgres / MySQL / MariaDB, or even network
 * connectivity. Pairs nicely with Flyway-style runtime migrations where the
 * same file is the schema authority both at codegen time (here) and at app
 * startup (Flyway / Liquibase).</p>
 *
 * <h2>Supported DDL (v1)</h2>
 * <ul>
 *   <li>{@code CREATE TABLE} with columns, types, NOT NULL, DEFAULT, PRIMARY KEY
 *       (inline + table-level), UNIQUE (inline + table-level), inline FK via
 *       {@code REFERENCES other(col)}.</li>
 *   <li>{@code ALTER TABLE … ADD CONSTRAINT … FOREIGN KEY (…) REFERENCES …}
 *       — the most common out-of-line FK form.</li>
 *   <li>{@code CREATE TYPE foo AS ENUM ('a','b')} (Postgres) — pre-extracted
 *       via regex since JSqlParser doesn't model Postgres ENUM types as a
 *       first-class statement.</li>
 *   <li>Inline column ENUMs: {@code col ENUM('a','b')} (MySQL / MariaDB) —
 *       the values are pulled from the column-type argument list.</li>
 *   <li>{@code COMMENT ON TABLE …} / {@code COMMENT ON COLUMN …} (Postgres).</li>
 *   <li>Inline {@code COMMENT 'text'} (MySQL / MariaDB).</li>
 *   <li>{@code AUTO_INCREMENT}, {@code SERIAL}, {@code BIGSERIAL},
 *       {@code GENERATED … AS IDENTITY} → autoIncrement flag.</li>
 * </ul>
 *
 * <h2>Out of scope (skipped with a warning, parsing continues)</h2>
 * <p>Triggers, functions, views, materialized views, partitions, generated
 * columns, CHECK enforcement, {@code CREATE INDEX}, {@code DOMAIN},
 * {@code EXTENSION}, {@code GRANT}/{@code REVOKE}, cross-schema FKs.
 * Statements JSqlParser can't parse trigger a {@code WARN} log identifying the
 * snippet, then we move on. {@code CREATE INDEX} and other no-ops for codegen
 * purposes are silently skipped.</p>
 *
 * <h2>Limitations vs JDBC introspection</h2>
 * <ul>
 *   <li>The {@code .sql} file is the only authority — we don't see runtime
 *       schema drift (a column added by a migration the file doesn't know
 *       about will be invisible).</li>
 *   <li>Owning-side relationships only — the {@link io.umaboot.core.relationship.RelationshipEngine}
 *       handles inverse-side and junction-table detection, exactly as it does
 *       for live-DB introspection.</li>
 * </ul>
 */
public final class SqlFileIntrospector implements Introspector {

    private static final Logger LOG = LoggerFactory.getLogger(SqlFileIntrospector.class);

    /** Matches {@code CREATE TYPE foo AS ENUM ('a', 'b', ...);} — Postgres ENUM declarations. */
    private static final Pattern PG_CREATE_TYPE_ENUM = Pattern.compile(
            "CREATE\\s+TYPE\\s+([A-Za-z_][\\w$.]*)\\s+AS\\s+ENUM\\s*\\(([^)]*)\\)\\s*;",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** Matches {@code CREATE INDEX …;} statements which we silently skip. */
    private static final Pattern CREATE_INDEX = Pattern.compile(
            "CREATE\\s+(UNIQUE\\s+)?INDEX[^;]*;",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** Strips MySQL/MariaDB table-options gunk that JSqlParser tolerates but produces noise on. */
    private static final Pattern STRIP_BACKTICKS = Pattern.compile("`");

    private final String sqlText;
    private final String dialectHint;

    /**
     * @param sqlText     full SQL DDL contents (already loaded, UTF-8)
     * @param dialectHint one of {@code "postgresql"}, {@code "mysql"}, {@code "mariadb"}.
     *                    Used as a soft routing hint for parser quirks; not strictly
     *                    enforced — the parser tolerates dialect mixing.
     */
    public SqlFileIntrospector(String sqlText, String dialectHint) {
        this.sqlText = Objects.requireNonNull(sqlText, "sqlText");
        this.dialectHint = (dialectHint == null ? "postgresql" : dialectHint).toLowerCase(Locale.ROOT);
    }

    @Override
    public SchemaModel introspect(String schemaName) throws SQLException {
        Objects.requireNonNull(schemaName, "schemaName");

        // 1. Pre-extract Postgres ENUM types (JSqlParser doesn't model these).
        Map<String, List<String>> enumTypes = extractPostgresEnums(sqlText);

        // 2. Strip statements JSqlParser would choke on or that don't matter for codegen.
        String cleaned = preprocess(sqlText);

        // 3. Parse remaining statements. We try the whole batch first; on failure we fall back
        //    to per-statement parsing so a single bad statement doesn't kill everything.
        List<Statement> statements = parseStatements(cleaned);

        // 4. Walk statements: collect tables, then later apply ALTER FK + COMMENT ON deltas.
        Map<String, MutableTable> tablesByName = new LinkedHashMap<>();
        List<DelayedFk> delayedFks = new ArrayList<>();
        List<DelayedComment> delayedComments = new ArrayList<>();

        for (Statement st : statements) {
            if (st instanceof CreateTable ct) {
                MutableTable mt = readCreateTable(ct, schemaName, enumTypes);
                tablesByName.put(mt.name.toLowerCase(Locale.ROOT), mt);
            } else if (st instanceof Alter alter) {
                collectAlterFks(alter, delayedFks);
            } else if (st instanceof Comment cmt) {
                collectComment(cmt, delayedComments);
            } else {
                LOG.debug("Skipping unsupported statement: {}", st.getClass().getSimpleName());
            }
        }

        // 5. Apply ALTER TABLE FK additions to their tables.
        for (DelayedFk fk : delayedFks) {
            MutableTable mt = tablesByName.get(fk.fromTable.toLowerCase(Locale.ROOT));
            if (mt == null) {
                LOG.warn("ALTER TABLE references unknown table {}; skipping FK", fk.fromTable);
                continue;
            }
            mt.relationships.add(buildRelationship(
                    fk.fromTable, fk.toTable, fk.fromColumns, fk.toColumns,
                    mt.uniqueConstraints));
        }

        // 6. Apply COMMENT ON deltas.
        for (DelayedComment dc : delayedComments) {
            MutableTable mt = tablesByName.get(dc.tableName.toLowerCase(Locale.ROOT));
            if (mt == null) {
                LOG.warn("COMMENT references unknown table {}; skipping", dc.tableName);
                continue;
            }
            if (dc.columnName == null) {
                mt.comment = dc.text;
            } else {
                int idx = indexOfColumn(mt.columns, dc.columnName);
                if (idx >= 0) {
                    mt.columns.set(idx, withComment(mt.columns.get(idx), dc.text));
                }
            }
        }

        // 7. Materialize TableModel list, preserving insertion order.
        List<TableModel> tables = new ArrayList<>();
        for (MutableTable mt : tablesByName.values()) {
            tables.add(new TableModel(
                    mt.name, mt.schema, mt.comment,
                    mt.columns, mt.primaryKey, mt.uniqueConstraints, mt.relationships, false));
        }
        return new SchemaModel(schemaName, tables);
    }

    // ------------------------------------------------------------------ preprocessing

    /** Pulls {@code CREATE TYPE ... AS ENUM (...)} statements out of the SQL and into a map. */
    static Map<String, List<String>> extractPostgresEnums(String sql) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        Matcher m = PG_CREATE_TYPE_ENUM.matcher(sql);
        while (m.find()) {
            String name = stripSchemaPrefix(m.group(1));
            String body = m.group(2);
            List<String> values = parseQuotedCsv(body);
            out.put(name.toLowerCase(Locale.ROOT), values);
        }
        return out;
    }

    /** Strips noise that JSqlParser doesn't need to see and won't always tolerate. */
    static String preprocess(String sql) {
        // Strip CREATE TYPE … AS ENUM blocks (we already extracted them).
        String s = PG_CREATE_TYPE_ENUM.matcher(sql).replaceAll("");
        // Drop CREATE INDEX — irrelevant for codegen, JSqlParser handles them but we skip anyway.
        s = CREATE_INDEX.matcher(s).replaceAll("");
        // Backticks around identifiers are MySQL-only; JSqlParser usually copes,
        // but stripping them keeps statement-text fallback paths simpler.
        s = STRIP_BACKTICKS.matcher(s).replaceAll("");
        // Drop common Postgres-only DROP IF EXISTS prologues that the file may carry —
        // they're either tables (fine), types (we already handled the CREATE TYPE), or
        // simply harmless to skip.
        return s;
    }

    private List<Statement> parseStatements(String sql) {
        if (sql == null || sql.trim().isEmpty()) return List.of();
        try {
            Statements parsed = CCJSqlParserUtil.parseStatements(sql);
            if (parsed == null || parsed.getStatements() == null) return List.of();
            return parsed.getStatements();
        } catch (JSQLParserException ex) {
            LOG.warn("Bulk parse failed ({}); falling back to per-statement parsing", ex.getMessage());
            return perStatementParse(sql);
        }
    }

    /** Last-resort: split by semicolon and parse each piece, tolerating individual failures. */
    private List<Statement> perStatementParse(String sql) {
        List<Statement> out = new ArrayList<>();
        // Naive split — the preprocessor already removed string-literal-heavy CREATE TYPE blocks.
        String[] pieces = sql.split(";");
        int idx = 0;
        for (String piece : pieces) {
            idx++;
            String trimmed = piece.trim();
            if (trimmed.isEmpty()) continue;
            try {
                Statement s = CCJSqlParserUtil.parse(trimmed);
                out.add(s);
            } catch (JSQLParserException ex) {
                LOG.warn("Skipping unparseable SQL statement #{} ({}): {}", idx, ex.getMessage(),
                        truncate(trimmed));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ CREATE TABLE

    private MutableTable readCreateTable(CreateTable ct, String schema,
                                          Map<String, List<String>> enumTypes) {
        String name = stripSchemaPrefix(ct.getTable().getName());
        MutableTable mt = new MutableTable();
        mt.name = name;
        mt.schema = schema;
        mt.comment = "";

        // Columns + inline column constraints.
        if (ct.getColumnDefinitions() != null) {
            for (ColumnDefinition cd : ct.getColumnDefinitions()) {
                ColumnContext cc = readColumnDefinition(cd, enumTypes);
                mt.columns.add(cc.column);
                if (cc.primaryKey) mt.primaryKeyFromInline.add(cc.column.name());
                if (cc.unique) mt.uniqueConstraints.add(List.of(cc.column.name()));
                if (cc.inlineFkTable != null) {
                    mt.relationships.add(buildRelationship(
                            name, cc.inlineFkTable,
                            List.of(cc.column.name()),
                            cc.inlineFkColumns,
                            mt.uniqueConstraints));
                }
            }
        }

        // Table-level constraints: PRIMARY KEY, UNIQUE, FOREIGN KEY.
        // JSqlParser also surfaces CHECK constraints here as NamedConstraint with no column
        // list — calling getColumnsNames() on those NPEs internally. Skip them.
        if (ct.getIndexes() != null) {
            for (Index idx : ct.getIndexes()) {
                String indexType = idx.getType() == null ? "" : idx.getType().toUpperCase(Locale.ROOT);
                if (indexType.contains("CHECK")) continue;
                if (idx.getColumns() == null || idx.getColumns().isEmpty()) continue;
                List<String> cols = unwrapColumnNames(idx.getColumnsNames());
                if (cols.isEmpty()) continue;
                if (indexType.contains("PRIMARY")) {
                    mt.primaryKeyTableLevel.addAll(cols);
                } else if (idx.getClass().getSimpleName().equals("ForeignKeyIndex")
                        || idx instanceof ForeignKeyIndex) {
                    ForeignKeyIndex fk = (ForeignKeyIndex) idx;
                    String toTable = stripSchemaPrefix(fk.getTable().getName());
                    List<String> toCols = unwrapColumnNames(fk.getReferencedColumnNames());
                    mt.relationships.add(buildRelationship(
                            name, toTable, cols, toCols, mt.uniqueConstraints));
                } else if (indexType.contains("UNIQUE")) {
                    mt.uniqueConstraints.add(cols);
                }
            }
        }

        // Pick the more authoritative PK source (table-level wins; otherwise inline).
        mt.primaryKey = !mt.primaryKeyTableLevel.isEmpty()
                ? mt.primaryKeyTableLevel
                : mt.primaryKeyFromInline;

        // Mark column.primaryKey on the column models that participate.
        Set<String> pkSet = new LinkedHashSet<>();
        for (String c : mt.primaryKey) pkSet.add(c.toLowerCase(Locale.ROOT));
        for (int i = 0; i < mt.columns.size(); i++) {
            ColumnModel c = mt.columns.get(i);
            if (pkSet.contains(c.name().toLowerCase(Locale.ROOT)) && !c.primaryKey()) {
                mt.columns.set(i, withPrimaryKey(c, true));
            }
        }
        return mt;
    }

    private ColumnContext readColumnDefinition(ColumnDefinition cd,
                                               Map<String, List<String>> enumTypes) {
        String columnName = stripQuotes(cd.getColumnName());
        String typeName = cd.getColDataType() == null ? "" : cd.getColDataType().getDataType();
        List<String> typeArgs = cd.getColDataType() == null ? List.of()
                : nullSafe(cd.getColDataType().getArgumentsStringList());

        // Detect inline column ENUM (MySQL/MariaDB). Postgres native enum is recognised by
        // the type name matching one of the previously-extracted CREATE TYPE entries.
        List<String> enumValues = List.of();
        if ("enum".equalsIgnoreCase(typeName) && !typeArgs.isEmpty()) {
            enumValues = unquoteAll(typeArgs);
        } else if (enumTypes.containsKey(typeName.toLowerCase(Locale.ROOT))) {
            enumValues = enumTypes.get(typeName.toLowerCase(Locale.ROOT));
        }

        // Reconstruct full type string for size/scale parsing (varchar(255), numeric(10,2)).
        String rawTypeForSize = typeName;
        if (!typeArgs.isEmpty() && !"enum".equalsIgnoreCase(typeName)) {
            rawTypeForSize = typeName + "(" + String.join(",", typeArgs) + ")";
        }
        int[] sizeScale = SqlTypeMapper.parseSizeAndScale(rawTypeForSize);

        // Walk column specs (post-type tokens) to pick up NOT NULL / DEFAULT / AUTO_INCREMENT / etc.
        boolean nullable = true;
        boolean primaryKey = false;
        boolean autoIncrement = SqlTypeMapper.isPostgresSerial(typeName);
        boolean unique = false;
        String defaultValue = null;
        String comment = "";
        String inlineFkTable = null;
        List<String> inlineFkColumns = List.of();

        List<String> specs = nullSafe(cd.getColumnSpecs());
        for (int i = 0; i < specs.size(); i++) {
            String tok = specs.get(i).toUpperCase(Locale.ROOT);
            switch (tok) {
                case "NOT" -> {
                    if (i + 1 < specs.size() && "NULL".equalsIgnoreCase(specs.get(i + 1))) {
                        nullable = false;
                        i++;
                    }
                }
                case "NULL" -> nullable = true;
                case "PRIMARY" -> {
                    if (i + 1 < specs.size() && "KEY".equalsIgnoreCase(specs.get(i + 1))) {
                        primaryKey = true;
                        i++;
                    }
                }
                case "UNIQUE" -> unique = true;
                case "AUTO_INCREMENT", "AUTOINCREMENT" -> autoIncrement = true;
                case "GENERATED" -> autoIncrement = autoIncrement || lookaheadContainsIdentity(specs, i);
                case "DEFAULT" -> {
                    if (i + 1 < specs.size()) {
                        defaultValue = stripQuotes(specs.get(i + 1));
                        i++;
                    }
                }
                case "COMMENT" -> {
                    if (i + 1 < specs.size()) {
                        comment = stripQuotes(specs.get(i + 1));
                        i++;
                    }
                }
                case "REFERENCES" -> {
                    // REFERENCES other(col[, col2]) → inline FK
                    if (i + 1 < specs.size()) {
                        inlineFkTable = stripSchemaPrefix(stripQuotes(specs.get(i + 1)));
                        i++;
                        if (i + 1 < specs.size() && specs.get(i + 1).startsWith("(")) {
                            String paren = specs.get(i + 1);
                            inlineFkColumns = parseParenList(paren);
                            i++;
                        } else {
                            // PostgreSQL allows REFERENCES tbl  (no column list) → references tbl's PK.
                            // We don't know the PK here; record an empty list and let RelationshipEngine
                            // treat it as a simple reference (the renderer handles single-PK FKs).
                            inlineFkColumns = List.of();
                        }
                    }
                }
                default -> { /* ignore CHECK(...), COLLATE, CHARACTER SET, etc. */ }
            }
        }

        ColumnModel column = new ColumnModel(
                columnName,
                SqlTypeMapper.toJdbcType(typeName),
                SqlTypeMapper.canonicalSqlType(typeName),
                sizeScale[0],
                sizeScale[1],
                nullable,
                primaryKey,
                autoIncrement,
                defaultValue,
                comment,
                enumValues);

        ColumnContext cc = new ColumnContext();
        cc.column = column;
        cc.primaryKey = primaryKey;
        cc.unique = unique;
        cc.inlineFkTable = inlineFkTable;
        cc.inlineFkColumns = inlineFkColumns;
        return cc;
    }

    private boolean lookaheadContainsIdentity(List<String> specs, int from) {
        for (int j = from; j < Math.min(from + 4, specs.size()); j++) {
            if ("IDENTITY".equalsIgnoreCase(specs.get(j))) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ ALTER TABLE FK

    private void collectAlterFks(Alter alter, List<DelayedFk> out) {
        String fromTable = alter.getTable() == null ? null : stripSchemaPrefix(alter.getTable().getName());
        if (fromTable == null) return;

        if (alter.getAlterExpressions() == null) return;
        for (AlterExpression ax : alter.getAlterExpressions()) {
            Index idx = ax.getIndex();
            if (idx instanceof ForeignKeyIndex fk) {
                List<String> fromCols = unwrapColumnNames(fk.getColumnsNames());
                List<String> toCols = unwrapColumnNames(fk.getReferencedColumnNames());
                String toTable = stripSchemaPrefix(fk.getTable().getName());
                out.add(new DelayedFk(fromTable, toTable, fromCols, toCols));
            }
        }
    }

    // ------------------------------------------------------------------ COMMENT ON

    private void collectComment(Comment cmt, List<DelayedComment> out) {
        // Postgres COMMENT ON TABLE foo IS 'text';  /  COMMENT ON COLUMN foo.bar IS 'text';
        String text = cmt.getComment() == null ? "" : stripQuotes(cmt.getComment().toString());
        if (cmt.getTable() != null) {
            out.add(new DelayedComment(stripSchemaPrefix(cmt.getTable().getName()), null, text));
        } else if (cmt.getColumn() != null) {
            String tableName = cmt.getColumn().getTable() == null
                    ? null
                    : stripSchemaPrefix(cmt.getColumn().getTable().getName());
            String columnName = cmt.getColumn().getColumnName();
            if (tableName != null && columnName != null) {
                out.add(new DelayedComment(tableName, stripQuotes(columnName), text));
            }
        }
    }

    // ------------------------------------------------------------------ relationship building

    private RelationshipModel buildRelationship(String fromTable, String toTable,
                                                List<String> fromCols, List<String> toCols,
                                                List<List<String>> uniqueConstraints) {
        boolean oneToOne = uniqueConstraints.stream()
                .anyMatch(uc -> uc.size() == fromCols.size() && uc.containsAll(fromCols));
        RelationshipType type = oneToOne ? new RelationshipType.OneToOne() : new RelationshipType.ManyToOne();
        String fieldName = deriveFieldName(fromCols, toTable);
        boolean selfRef = toTable.equalsIgnoreCase(fromTable);
        return new RelationshipModel(fromTable, toTable, type, fromCols, toCols,
                fieldName, true, selfRef);
    }

    private static String deriveFieldName(List<String> fromColumns, String referencedTable) {
        if (fromColumns.size() == 1) {
            String col = fromColumns.get(0).toLowerCase(Locale.ROOT);
            if (col.endsWith("_id") && col.length() > 3) {
                return col.substring(0, col.length() - 3);
            }
        }
        return referencedTable.toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------ small helpers

    private static String stripSchemaPrefix(String name) {
        if (name == null) return null;
        String n = stripQuotes(name);
        int dot = n.indexOf('.');
        return dot >= 0 ? n.substring(dot + 1) : n;
    }

    private static String stripQuotes(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.length() >= 2) {
            char first = t.charAt(0);
            char last = t.charAt(t.length() - 1);
            if ((first == '\'' && last == '\'')
                    || (first == '"' && last == '"')
                    || (first == '`' && last == '`')) {
                t = t.substring(1, t.length() - 1);
                // Postgres / MySQL escape '' inside string literals — collapse them.
                if (first == '\'') t = t.replace("''", "'");
                if (first == '"') t = t.replace("\"\"", "\"");
            }
        }
        return t;
    }

    private static List<String> unwrapColumnNames(List<String> raw) {
        if (raw == null) return List.of();
        List<String> out = new ArrayList<>(raw.size());
        for (String s : raw) out.add(stripQuotes(s));
        return out;
    }

    private static List<String> unquoteAll(Collection<String> raw) {
        List<String> out = new ArrayList<>(raw.size());
        for (String s : raw) out.add(stripQuotes(s));
        return out;
    }

    private static List<String> parseQuotedCsv(String body) {
        // Splits "'a', 'b', 'c'" → [a, b, c] — naive but adequate for ENUM literal lists.
        List<String> out = new ArrayList<>();
        for (String piece : body.split(",")) {
            String t = piece.trim();
            if (!t.isEmpty()) out.add(stripQuotes(t));
        }
        return out;
    }

    private static List<String> parseParenList(String parenWrapped) {
        // "(a, b, c)" → [a, b, c]
        String inner = parenWrapped.trim();
        if (inner.startsWith("(")) inner = inner.substring(1);
        if (inner.endsWith(")")) inner = inner.substring(0, inner.length() - 1);
        List<String> out = new ArrayList<>();
        for (String piece : inner.split(",")) {
            String t = stripQuotes(piece.trim());
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    private static <T> List<T> nullSafe(List<T> in) {
        return in == null ? List.of() : in;
    }

    private static int indexOfColumn(List<ColumnModel> cols, String name) {
        for (int i = 0; i < cols.size(); i++) {
            if (cols.get(i).name().equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    private static ColumnModel withComment(ColumnModel c, String comment) {
        return new ColumnModel(c.name(), c.jdbcType(), c.sqlType(), c.size(), c.scale(),
                c.nullable(), c.primaryKey(), c.autoIncrement(), c.defaultValue(), comment, c.enumValues());
    }

    private static ColumnModel withPrimaryKey(ColumnModel c, boolean pk) {
        return new ColumnModel(c.name(), c.jdbcType(), c.sqlType(), c.size(), c.scale(),
                c.nullable(), pk, c.autoIncrement(), c.defaultValue(), c.comment(), c.enumValues());
    }

    private static String truncate(String s) {
        return s.length() > 80 ? s.substring(0, 80) + "..." : s;
    }

    // ------------------------------------------------------------------ small mutable holders

    private static final class MutableTable {
        String name;
        String schema;
        String comment = "";
        final List<ColumnModel> columns = new ArrayList<>();
        final List<String> primaryKeyFromInline = new ArrayList<>();
        final List<String> primaryKeyTableLevel = new ArrayList<>();
        List<String> primaryKey = List.of();
        final List<List<String>> uniqueConstraints = new ArrayList<>();
        final List<RelationshipModel> relationships = new ArrayList<>();
    }

    private static final class ColumnContext {
        ColumnModel column;
        boolean primaryKey;
        boolean unique;
        String inlineFkTable;
        List<String> inlineFkColumns = List.of();
    }

    private record DelayedFk(String fromTable, String toTable,
                             List<String> fromColumns, List<String> toColumns) {}

    private record DelayedComment(String tableName, String columnName, String text) {}
}

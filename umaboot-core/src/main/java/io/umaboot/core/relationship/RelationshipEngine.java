package io.umaboot.core.relationship;

import io.umaboot.core.model.ColumnModel;
import io.umaboot.core.model.RelationshipModel;
import io.umaboot.core.model.RelationshipType;
import io.umaboot.core.model.SchemaModel;
import io.umaboot.core.model.TableModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Post-processes a {@link SchemaModel} to:
 * <ul>
 *   <li>Detect pure junction tables (PK = exactly two FK columns referencing different tables).</li>
 *   <li>Add inverse-side {@link RelationshipType.OneToMany} / {@link RelationshipType.OneToOne}
 *       relationships on parent tables for each owning-side ManyToOne / OneToOne.</li>
 *   <li>Replace junction-mediated relationships with two ManyToMany sides on the linked tables,
 *       and mark the junction table itself.</li>
 *   <li>Mark self-referencing relationships.</li>
 * </ul>
 */
public final class RelationshipEngine {

    private static final Logger LOG = LoggerFactory.getLogger(RelationshipEngine.class);

    /**
     * Returns a new {@link SchemaModel} with relationships fully resolved.
     */
    public SchemaModel analyze(SchemaModel schema) {
        Objects.requireNonNull(schema, "schema");

        // Build a working copy of relationships per table
        Map<String, List<RelationshipModel>> rels = new LinkedHashMap<>();
        Map<String, TableModel> tables = new LinkedHashMap<>();
        for (TableModel t : schema.tables()) {
            tables.put(key(t.name()), t);
            rels.put(key(t.name()), new ArrayList<>(t.relationships()));
        }

        // 1. Identify junction tables: PK consists of exactly the two (or more) FK columns,
        //    and table has only those FK columns plus optional non-key columns.
        //    For v0.1 we treat a junction strictly as: PK is two FK refs to two distinct tables,
        //    and the PK columns are exactly the union of FK columns.
        List<String> junctionTables = new ArrayList<>();
        for (TableModel t : schema.tables()) {
            if (isPureJunction(t)) {
                junctionTables.add(t.name());
            }
        }
        LOG.debug("Detected junction tables: {}", junctionTables);

        // 2. For each junction table, replace its two ManyToOne relationships with ManyToMany
        //    on the two linked tables.
        for (String junction : junctionTables) {
            TableModel jt = tables.get(key(junction));
            List<RelationshipModel> jrels = jt.relationships();
            if (jrels.size() < 2) continue;
            RelationshipModel a = jrels.get(0);
            RelationshipModel b = jrels.get(1);

            String tableA = a.toTable();
            String tableB = b.toTable();
            List<RelationshipModel> relsA = relationshipList(rels, tableA, junction, a);
            List<RelationshipModel> relsB = relationshipList(rels, tableB, junction, b);

            String fieldOnA = pluralize(tableB.toLowerCase());
            String fieldOnB = pluralize(tableA.toLowerCase());

            RelationshipModel onA = new RelationshipModel(
                    tableA, tableB,
                    new RelationshipType.ManyToMany(junction),
                    a.toColumns(), // PK cols of A
                    b.toColumns(), // PK cols of B
                    fieldOnA, true, tableA.equalsIgnoreCase(tableB));
            RelationshipModel onB = new RelationshipModel(
                    tableB, tableA,
                    new RelationshipType.ManyToMany(junction),
                    b.toColumns(),
                    a.toColumns(),
                    fieldOnB, false, tableA.equalsIgnoreCase(tableB));

            relsA.add(onA);
            relsB.add(onB);
            // Junction table keeps its own ManyToOne sides — they are still useful when the
            // junction carries extra non-key columns (which we treat as a regular entity in v2).
        }

        // 3. Add inverse-side OneToMany / OneToOne for every owning-side ManyToOne / OneToOne
        //    on non-junction tables.
        for (TableModel t : schema.tables()) {
            if (junctionTables.contains(t.name())) continue;
            for (RelationshipModel r : t.relationships()) {
                if (!r.owning()) continue;
                if (r.type() instanceof RelationshipType.ManyToOne) {
                    String fieldOnParent = pluralize(t.name().toLowerCase());
                    relationshipList(rels, r.toTable(), t.name(), r).add(new RelationshipModel(
                            r.toTable(), r.fromTable(),
                            new RelationshipType.OneToMany(),
                            r.toColumns(), r.fromColumns(),
                            fieldOnParent, false, r.selfReference()));
                } else if (r.type() instanceof RelationshipType.OneToOne) {
                    String fieldOnParent = t.name().toLowerCase();
                    relationshipList(rels, r.toTable(), t.name(), r).add(new RelationshipModel(
                            r.toTable(), r.fromTable(),
                            new RelationshipType.OneToOne(),
                            r.toColumns(), r.fromColumns(),
                            fieldOnParent, false, r.selfReference()));
                }
            }
        }

        // 4. Rebuild table list with updated relationships and junction flag.
        List<TableModel> rebuilt = new ArrayList<>();
        for (TableModel t : schema.tables()) {
            boolean junction = junctionTables.contains(t.name());
            rebuilt.add(t.withRelationships(rels.get(key(t.name()))).withJunction(junction));
        }
        return new SchemaModel(schema.schemaName(), rebuilt);
    }

    private static List<RelationshipModel> relationshipList(
            Map<String, List<RelationshipModel>> rels,
            String targetTable,
            String sourceTable,
            RelationshipModel relationship) {
        List<RelationshipModel> found = rels.get(key(targetTable));
        if (found != null) return found;
        throw new IllegalArgumentException(
                "Relationship target table '" + targetTable + "' referenced by table '" + sourceTable
                        + "' was not found in the parsed schema. "
                        + "Check that the schema file contains CREATE TABLE for '" + targetTable
                        + "' and that the SQL parser did not skip it. Relationship columns: "
                        + relationship.fromColumns() + " -> " + relationship.toColumns());
    }

    private static String key(String tableName) {
        return tableName == null ? "" : tableName.toLowerCase(Locale.ROOT);
    }

    private boolean isPureJunction(TableModel t) {
        // A junction table has:
        //  - PK with >= 2 columns
        //  - exactly two owning ManyToOne relationships (to two distinct tables)
        //  - PK columns are exactly the union of FK columns
        if (t.primaryKey().size() < 2) return false;
        List<RelationshipModel> owning = t.relationships().stream()
                .filter(RelationshipModel::owning)
                .filter(r -> r.type() instanceof RelationshipType.ManyToOne
                          || r.type() instanceof RelationshipType.OneToOne)
                .toList();
        if (owning.size() != 2) return false;
        if (owning.get(0).toTable().equalsIgnoreCase(owning.get(1).toTable())) return false;

        List<String> fkCols = new ArrayList<>();
        for (RelationshipModel r : owning) fkCols.addAll(r.fromColumns());
        if (fkCols.size() != t.primaryKey().size()) return false;
        if (!fkCols.stream().allMatch(c -> t.primaryKey().stream().anyMatch(p -> p.equalsIgnoreCase(c)))) {
            return false;
        }
        // No non-key non-FK extra columns of significance — for v0.1 we tolerate audit columns
        // (created_at / updated_at) by not enforcing strict column-count equality.
        long extraCols = t.columns().stream()
                .filter(c -> !c.primaryKey())
                .filter(c -> !isAuditColumn(c))
                .count();
        return extraCols == 0;
    }

    private boolean isAuditColumn(ColumnModel c) {
        String n = c.name().toLowerCase();
        return n.equals("created_at") || n.equals("updated_at")
                || n.equals("created_by") || n.equals("updated_by");
    }

    private static String pluralize(String name) {
        if (name.endsWith("s")) return name + "es";
        if (name.endsWith("y")) return name.substring(0, name.length() - 1) + "ies";
        return name + "s";
    }
}

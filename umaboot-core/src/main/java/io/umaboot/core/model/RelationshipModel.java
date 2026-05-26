package io.umaboot.core.model;

import java.util.List;
import java.util.Objects;

/**
 * A single relationship from one table to another.
 *
 * <p>Two records are produced per FK in most cases: an owning side
 * (e.g. {@code ManyToOne}) on the FK-holding table, and an inverse side
 * (e.g. {@code OneToMany}) on the referenced table.</p>
 *
 * @param fromTable   the table this relationship is attached to
 * @param toTable     the related table
 * @param type        the relationship kind
 * @param fromColumns columns on {@code fromTable} that participate (FK columns or PK on inverse side)
 * @param toColumns   referenced columns on {@code toTable}
 * @param fieldName   suggested Java field name (e.g. {@code customer}, {@code orders})
 * @param owning      true if this side owns the FK in the database
 * @param selfReference true if {@code fromTable} equals {@code toTable}
 */
public record RelationshipModel(
        String fromTable,
        String toTable,
        RelationshipType type,
        List<String> fromColumns,
        List<String> toColumns,
        String fieldName,
        boolean owning,
        boolean selfReference) {

    public RelationshipModel {
        Objects.requireNonNull(fromTable, "fromTable");
        Objects.requireNonNull(toTable, "toTable");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(fieldName, "fieldName");
        fromColumns = List.copyOf(Objects.requireNonNull(fromColumns, "fromColumns"));
        toColumns = List.copyOf(Objects.requireNonNull(toColumns, "toColumns"));
    }
}

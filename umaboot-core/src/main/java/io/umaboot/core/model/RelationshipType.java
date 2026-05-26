package io.umaboot.core.model;

/**
 * Sealed hierarchy of relationship kinds detected by {@code RelationshipEngine}.
 *
 * <p>Pattern-matched in generators to choose the correct JPA annotation
 * ({@code @OneToOne}, {@code @OneToMany}, {@code @ManyToOne}, {@code @ManyToMany}).</p>
 */
public sealed interface RelationshipType
        permits RelationshipType.OneToOne,
                RelationshipType.OneToMany,
                RelationshipType.ManyToOne,
                RelationshipType.ManyToMany {

    /** One-to-one relationship (FK column has UNIQUE constraint). */
    record OneToOne() implements RelationshipType {}

    /** One-to-many: the inverse side of a ManyToOne, owned by the other table. */
    record OneToMany() implements RelationshipType {}

    /** Many-to-one: this table holds the FK pointing to the parent. */
    record ManyToOne() implements RelationshipType {}

    /**
     * Many-to-many through a junction table.
     *
     * @param junctionTable the link/junction table name
     */
    record ManyToMany(String junctionTable) implements RelationshipType {}
}

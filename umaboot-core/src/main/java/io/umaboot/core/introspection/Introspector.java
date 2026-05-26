package io.umaboot.core.introspection;

import io.umaboot.core.model.SchemaModel;

import java.sql.SQLException;

/**
 * Reads a database schema and returns a {@link SchemaModel}.
 *
 * <p>Implementations are database-specific (Postgres, MySQL, ...) but expose a
 * common pure-Java contract. Connections are passed in by the caller and are
 * never closed by the introspector.</p>
 */
public interface Introspector {

    /**
     * Introspect the given schema using the supplied JDBC connection.
     *
     * @param schema the schema name (e.g. {@code public})
     * @return an immutable {@link SchemaModel} describing all tables, columns,
     *         constraints, and unprocessed relationships (without inverse-side
     *         resolution — that is the {@code RelationshipEngine}'s job)
     * @throws SQLException if metadata access fails
     */
    SchemaModel introspect(String schema) throws SQLException;
}

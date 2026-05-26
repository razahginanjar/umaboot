package io.umaboot.core.generator.persistence;

import io.umaboot.core.generator.GeneratedUnit;
import io.umaboot.core.generator.GeneratorContext;
import io.umaboot.core.model.SchemaModel;
import io.umaboot.core.model.TableModel;

import java.util.List;

/**
 * Pluggable persistence-layer file generator.
 *
 * <p>Each provider knows how to emit the persistence-specific files for a
 * single table (entity, repository / mapper, mapper XML, etc.) and any
 * project-wide files needed by that backend (extra POM dependencies and
 * application.yml fragments are surfaced via {@link #projectExtras(SchemaModel, GeneratorContext)}).</p>
 */
public interface PersistenceProvider {

    /** Identifier for selection: {@code jpa | mybatis | jooq}. */
    String id();

    /**
     * @return persistence-specific files for one table (entity + repository / mapper).
     *         Path is relative to the generated project root.
     */
    List<GeneratedUnit> generateForTable(TableModel table, GeneratorContext ctx);

    /**
     * @return persistence-specific project-wide files (e.g. mybatis-config.xml,
     *         jOOQ configuration, JPA-only extras). May be empty.
     */
    default List<GeneratedUnit> projectExtras(SchemaModel schema, GeneratorContext ctx) {
        return List.of();
    }
}

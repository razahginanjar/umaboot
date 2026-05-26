package io.umaboot.core.generator;

import io.umaboot.core.model.SchemaModel;

import java.util.List;

/**
 * A complete project generator for one architecture pattern (MVC, Hexagonal, DDD).
 *
 * <p>Implementations consume a fully-resolved {@link SchemaModel} (post relationship
 * engine, post table filter) and emit the full set of {@link GeneratedUnit}s
 * required to build a runnable Spring Boot project in the target architecture.</p>
 */
public interface ArchitectureGenerator {

    /** {@code mvc | hexagonal | ddd}. */
    String architecture();

    List<GeneratedUnit> generate(SchemaModel schema);
}

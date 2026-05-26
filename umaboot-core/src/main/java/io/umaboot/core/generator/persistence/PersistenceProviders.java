package io.umaboot.core.generator.persistence;

import io.umaboot.core.generator.GeneratorContext;
import io.umaboot.core.generator.jooq.JooqPersistenceProvider;
import io.umaboot.core.generator.jpa.JpaPersistenceProvider;
import io.umaboot.core.generator.mybatis.MyBatisPersistenceProvider;
import io.umaboot.core.template.TemplateEngine;

/**
 * Resolves the {@link PersistenceProvider} for a given configuration.
 */
public final class PersistenceProviders {

    private PersistenceProviders() {}

    public static PersistenceProvider forContext(GeneratorContext ctx, TemplateEngine engine) {
        if (ctx.isJpa()) return new JpaPersistenceProvider(engine);
        if (ctx.isMyBatis()) return new MyBatisPersistenceProvider(engine);
        if (ctx.isJooq()) return new JooqPersistenceProvider(engine);
        throw new IllegalArgumentException("Unsupported persistence: " + ctx.persistence());
    }
}

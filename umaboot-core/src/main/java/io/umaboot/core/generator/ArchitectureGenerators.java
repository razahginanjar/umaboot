package io.umaboot.core.generator;

import io.umaboot.core.generator.ddd.DddGenerator;
import io.umaboot.core.generator.hexagonal.HexagonalGenerator;
import io.umaboot.core.generator.mvc.MvcGenerator;
import io.umaboot.core.template.TemplateEngine;

/**
 * Resolves the {@link ArchitectureGenerator} for a given context.
 */
public final class ArchitectureGenerators {

    private ArchitectureGenerators() {}

    public static ArchitectureGenerator forContext(GeneratorContext ctx, TemplateEngine engine) {
        if (ctx.isHexagonal()) return new HexagonalGenerator(engine, ctx);
        if (ctx.isDdd()) return new DddGenerator(engine, ctx);
        return new MvcGenerator(engine, ctx);
    }
}

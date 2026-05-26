package io.umaboot.core.architecture;

import io.umaboot.core.architecture.layered.LayeredArchitectureRenderer;
import io.umaboot.core.generator.GeneratorContext;

/**
 * Factory that resolves the right {@link ArchitectureRenderer}.
 *
 * <p>v0.4: with dedicated MVC, Hexagonal, and DDD generators all emitting at
 * the correct paths, the renderer is the passthrough
 * {@link LayeredArchitectureRenderer} for every architecture. The
 * path-rewriting renderers ({@code HexagonalArchitectureRenderer},
 * {@code DddArchitectureRenderer}) are kept under the {@code architecture.*}
 * packages for users who want to relocate v0.1-style MVC output but they are
 * no longer the default.</p>
 */
public final class ArchitectureRenderers {

    private ArchitectureRenderers() {}

    public static ArchitectureRenderer forContext(GeneratorContext ctx) {
        return new LayeredArchitectureRenderer();
    }
}

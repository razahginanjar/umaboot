package io.umaboot.core.architecture;

import io.umaboot.core.generator.GeneratedUnit;

import java.nio.file.Path;
import java.util.List;

/**
 * Writes a list of {@link GeneratedUnit}s to disk, applying any
 * architecture-specific path remapping.
 *
 * <p>For MVC the input paths are already correct, so the renderer is a
 * straight passthrough; for Hexagonal and DDD (post-v0.1) the renderer
 * relocates files into the appropriate {@code domain/}, {@code application/},
 * {@code adapter/}, or {@code infrastructure/} layer directories.</p>
 */
public interface ArchitectureRenderer {

    /**
     * Render the given units into {@code outputDir}.
     *
     * @param units     generated units (path is relative to project root)
     * @param outputDir target directory (will be created if absent)
     * @return list of absolute paths actually written
     */
    List<Path> render(List<GeneratedUnit> units, Path outputDir);
}

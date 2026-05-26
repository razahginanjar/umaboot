package io.umaboot.core.architecture.layered;

import io.umaboot.core.architecture.ArchitectureRenderer;
import io.umaboot.core.generator.GeneratedUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * MVC / Layered architecture renderer.
 *
 * <p>The MVC unit paths emitted by {@code MvcJpaGenerator} already follow the
 * canonical {@code controller/}, {@code service/}, {@code repository/},
 * {@code entity/}, {@code dto/}, {@code mapper/}, {@code exception/} layout, so
 * the renderer is a straight passthrough that materializes them on disk.</p>
 */
public final class LayeredArchitectureRenderer implements ArchitectureRenderer {

    private static final Logger LOG = LoggerFactory.getLogger(LayeredArchitectureRenderer.class);

    @Override
    public List<Path> render(List<GeneratedUnit> units, Path outputDir) {
        Objects.requireNonNull(units, "units");
        Objects.requireNonNull(outputDir, "outputDir");
        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create output dir: " + outputDir, e);
        }

        List<Path> written = new ArrayList<>();
        for (GeneratedUnit u : units) {
            Path target = outputDir.resolve(u.relativePath().replace('/', java.io.File.separatorChar));
            try {
                Files.createDirectories(target.getParent());
                Files.writeString(target, u.content(), StandardCharsets.UTF_8);
                written.add(target);
                LOG.debug("Wrote {}", target);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to write " + target, e);
            }
        }
        return written;
    }
}

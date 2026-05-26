package io.umaboot.core.architecture.ddd;

import io.umaboot.core.architecture.ArchitectureRenderer;
import io.umaboot.core.architecture.layered.LayeredArchitectureRenderer;
import io.umaboot.core.generator.GeneratedUnit;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * DDD architecture renderer.
 *
 * <p>Rewrites paths into the DDD layout:</p>
 * <ul>
 *   <li>{@code entity/} → {@code infrastructure/persistence/}</li>
 *   <li>{@code repository/} → {@code infrastructure/persistence/}</li>
 *   <li>{@code service/} (interface) → {@code application/}</li>
 *   <li>{@code service/impl/} → {@code application/}</li>
 *   <li>{@code controller/} → {@code interfaces/rest/}</li>
 *   <li>{@code dto/} → {@code interfaces/rest/dto/}</li>
 *   <li>{@code mapper/} → {@code interfaces/rest/mapper/}</li>
 *   <li>{@code exception/} → {@code domain/exception/}</li>
 * </ul>
 *
 * <p>Note (matching the spec): true DDD generation requires user to confirm
 * aggregate roots — that interactive flow is post-v0.2. This renderer
 * relocates the v0.2 generated files into a DDD-shaped tree to give
 * teams a starting point.</p>
 */
public final class DddArchitectureRenderer implements ArchitectureRenderer {

    private final LayeredArchitectureRenderer delegate = new LayeredArchitectureRenderer();

    @Override
    public List<Path> render(List<GeneratedUnit> units, Path outputDir) {
        List<GeneratedUnit> remapped = new ArrayList<>(units.size());
        for (GeneratedUnit u : units) remapped.add(remap(u));
        return delegate.render(remapped, outputDir);
    }

    private GeneratedUnit remap(GeneratedUnit u) {
        String path = u.relativePath();
        if (!path.startsWith("src/main/java/")) return u;
        String content = u.content();

        if (path.contains("/entity/")) {
            return new GeneratedUnit(
                    path.replace("/entity/", "/infrastructure/persistence/"),
                    replacePackage(content, "entity", "infrastructure.persistence"));
        }
        if (path.contains("/repository/")) {
            return new GeneratedUnit(
                    path.replace("/repository/", "/infrastructure/persistence/"),
                    replacePackage(content, "repository", "infrastructure.persistence"));
        }
        if (path.contains("/service/impl/")) {
            return new GeneratedUnit(
                    path.replace("/service/impl/", "/application/"),
                    replacePackage(content, "service.impl", "application"));
        }
        if (path.contains("/service/")) {
            return new GeneratedUnit(
                    path.replace("/service/", "/application/"),
                    replacePackage(content, "service", "application"));
        }
        if (path.contains("/controller/")) {
            return new GeneratedUnit(
                    path.replace("/controller/", "/interfaces/rest/"),
                    replacePackage(content, "controller", "interfaces.rest"));
        }
        if (path.contains("/dto/")) {
            return new GeneratedUnit(
                    path.replace("/dto/", "/interfaces/rest/dto/"),
                    replacePackage(content, "dto", "interfaces.rest.dto"));
        }
        if (path.contains("/mapper/")) {
            return new GeneratedUnit(
                    path.replace("/mapper/", "/interfaces/rest/mapper/"),
                    replacePackage(content, "mapper", "interfaces.rest.mapper"));
        }
        if (path.contains("/exception/")) {
            return new GeneratedUnit(
                    path.replace("/exception/", "/domain/exception/"),
                    replacePackage(content, "exception", "domain.exception"));
        }
        return u;
    }

    private static String replacePackage(String content, String oldSuffix, String newSuffix) {
        return content
                .replace("." + oldSuffix + ";", "." + newSuffix + ";")
                .replace("." + oldSuffix + ".", "." + newSuffix + ".");
    }
}

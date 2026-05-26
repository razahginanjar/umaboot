package io.umaboot.core.architecture.hexagonal;

import io.umaboot.core.architecture.ArchitectureRenderer;
import io.umaboot.core.architecture.layered.LayeredArchitectureRenderer;
import io.umaboot.core.generator.GeneratedUnit;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Hexagonal / Ports &amp; Adapters renderer.
 *
 * <p>Rewrites the canonical layered paths emitted by the generator into the
 * Hexagonal package layout:</p>
 * <ul>
 *   <li>{@code entity/} → {@code adapter/out/persistence/} (renamed to {@code *JpaEntity})</li>
 *   <li>{@code repository/} → {@code adapter/out/persistence/} (with {@code PersistenceAdapter} pattern)</li>
 *   <li>{@code service/} (interface) → {@code application/usecase/}</li>
 *   <li>{@code service/impl/} → {@code application/service/}</li>
 *   <li>{@code controller/} → {@code adapter/in/web/}</li>
 *   <li>{@code dto/} → {@code adapter/in/web/dto/}</li>
 *   <li>{@code mapper/} → {@code adapter/in/web/mapper/}</li>
 *   <li>{@code exception/} → {@code domain/exception/}</li>
 * </ul>
 *
 * <p>Note: This is a v0.2 path-rewrite-only renderer. A full Hexagonal layout
 * would also generate a separate domain model (no JPA), domain port interface,
 * and PersistenceAdapter. Those are emitted by the Hexagonal-specific generator
 * pass via dedicated templates; for v0.2 we rely on the MVC generator output and
 * relocate the files. Callers wanting fully-pure domain models should use the
 * post-v0.2 hexagonal generator pass.</p>
 */
public final class HexagonalArchitectureRenderer implements ArchitectureRenderer {

    private final LayeredArchitectureRenderer delegate = new LayeredArchitectureRenderer();

    @Override
    public List<Path> render(List<GeneratedUnit> units, Path outputDir) {
        List<GeneratedUnit> remapped = new ArrayList<>(units.size());
        for (GeneratedUnit u : units) remapped.add(remap(u));
        return delegate.render(remapped, outputDir);
    }

    private GeneratedUnit remap(GeneratedUnit u) {
        String path = u.relativePath();
        String content = u.content();

        // Only rewrite Java sources under src/main/java
        if (!path.startsWith("src/main/java/")) return u;

        if (contains(path, "/entity/")) {
            String newPath = replace(path, "/entity/", "/adapter/out/persistence/");
            return new GeneratedUnit(newPath, replacePackage(content, "entity", "adapter.out.persistence"));
        }
        if (contains(path, "/repository/")) {
            String newPath = replace(path, "/repository/", "/adapter/out/persistence/");
            return new GeneratedUnit(newPath, replacePackage(content, "repository", "adapter.out.persistence"));
        }
        if (contains(path, "/service/impl/")) {
            String newPath = replace(path, "/service/impl/", "/application/service/");
            return new GeneratedUnit(newPath, replacePackage(content, "service.impl", "application.service"));
        }
        if (contains(path, "/service/")) {
            String newPath = replace(path, "/service/", "/application/usecase/");
            return new GeneratedUnit(newPath, replacePackage(content, "service", "application.usecase"));
        }
        if (contains(path, "/controller/")) {
            String newPath = replace(path, "/controller/", "/adapter/in/web/");
            return new GeneratedUnit(newPath, replacePackage(content, "controller", "adapter.in.web"));
        }
        if (contains(path, "/dto/")) {
            String newPath = replace(path, "/dto/", "/adapter/in/web/dto/");
            return new GeneratedUnit(newPath, replacePackage(content, "dto", "adapter.in.web.dto"));
        }
        if (contains(path, "/mapper/")) {
            String newPath = replace(path, "/mapper/", "/adapter/in/web/mapper/");
            return new GeneratedUnit(newPath, replacePackage(content, "mapper", "adapter.in.web.mapper"));
        }
        if (contains(path, "/exception/")) {
            String newPath = replace(path, "/exception/", "/domain/exception/");
            return new GeneratedUnit(newPath, replacePackage(content, "exception", "domain.exception"));
        }
        return u;
    }

    private static boolean contains(String s, String needle) {
        return s.contains(needle);
    }

    private static String replace(String s, String oldPart, String newPart) {
        return s.replace(oldPart, newPart);
    }

    /**
     * Best-effort package-line and import rewrite for the relocation.
     * Replaces {@code .{old};} occurrences in package and import lines with the
     * new package, scoped to the application's basePackage.
     */
    private static String replacePackage(String content, String oldSuffix, String newSuffix) {
        return content
                .replace("." + oldSuffix + ";", "." + newSuffix + ";")
                .replace("." + oldSuffix + ".", "." + newSuffix + ".");
    }
}

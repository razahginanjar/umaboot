package io.umaboot.core.config;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves the configured {@code generation.outputDir} into an absolute filesystem
 * path the same way regardless of which surface (CLI / IntelliJ plugin / VS Code
 * extension) invoked it.
 *
 * <p>Semantics:</p>
 * <ul>
 *   <li><b>Absolute path</b> — returned as-is, normalized.</li>
 *   <li><b>Relative path with a known config file</b> — resolved against the
 *       config file's <em>parent directory</em>. So {@code outputDir: ./generated}
 *       in {@code /proj/umaboot.yaml} always lands in {@code /proj/generated/},
 *       regardless of where the CLI was invoked from.</li>
 *   <li><b>Relative path with no config file</b> — resolved against the JVM's
 *       working directory as a last-resort fallback.</li>
 * </ul>
 *
 * <p>Special case: {@code outputDir: .} resolves to the config file's parent
 * directory itself — the standard "output into the project directory" workflow.</p>
 */
public final class OutputDirResolver {

    private OutputDirResolver() {}

    /**
     * Resolve {@code config.generation().outputDir()} against {@code configFile}'s
     * parent.
     *
     * @param config       a loaded configuration; must not be {@code null}
     * @param configFile   the path to {@code umaboot.yaml} the config was loaded from;
     *                     may be {@code null} (then CWD is used as fallback)
     * @return absolute, normalized path
     */
    public static Path resolve(UmabootConfig config, Path configFile) {
        if (config == null) throw new IllegalArgumentException("config must not be null");
        Path raw = Paths.get(config.generation().outputDir());
        if (raw.isAbsolute()) return raw.toAbsolutePath().normalize();
        Path parent = (configFile != null) ? configFile.toAbsolutePath().getParent() : null;
        if (parent == null) return raw.toAbsolutePath().normalize();
        return parent.resolve(raw).normalize();
    }
}

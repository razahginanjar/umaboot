package io.umaboot.intellij;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves the Umaboot config file in a project root, preferring the new
 * {@code umaboot.yaml} but falling back to the legacy {@code crudforge.yaml}
 * when only the latter is present.
 *
 * <p>Centralizing this so the action, settings panel, line-marker provider,
 * and file-icon provider all agree on which file is "the config" — including
 * legacy projects that haven't been renamed yet.</p>
 */
public final class UmabootConfigLocator {

    /** Preferred new name. */
    public static final String PRIMARY = "umaboot.yaml";

    /** Legacy name kept for backwards compatibility. */
    public static final String LEGACY = "crudforge.yaml";

    private UmabootConfigLocator() {}

    /**
     * Returns the existing config file under {@code projectRoot}: prefers
     * {@code umaboot.yaml}; falls back to {@code crudforge.yaml}; otherwise
     * returns {@code projectRoot/umaboot.yaml} (the path the user would create
     * for a brand-new project).
     */
    public static Path findConfigFile(Path projectRoot) {
        Path primary = projectRoot.resolve(PRIMARY);
        if (Files.exists(primary)) return primary;
        Path legacy = projectRoot.resolve(LEGACY);
        if (Files.exists(legacy)) return legacy;
        return primary;
    }

    /** True if {@code fileName} matches the new or legacy config-file name (case-insensitive). */
    public static boolean isConfigFileName(String fileName) {
        return PRIMARY.equalsIgnoreCase(fileName) || LEGACY.equalsIgnoreCase(fileName);
    }
}

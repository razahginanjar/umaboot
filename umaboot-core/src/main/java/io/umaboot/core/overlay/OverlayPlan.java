package io.umaboot.core.overlay;

import io.umaboot.core.diff.DiffEngine;
import io.umaboot.core.generator.GeneratedUnit;

import java.util.List;

/**
 * Result of planning an overlay generation against an existing project.
 *
 * <p>Overlay mode treats changed existing files as merge work instead of
 * immediately overwriting them.</p>
 */
public record OverlayPlan(
        DiffEngine.DiffResult diff,
        List<GeneratedUnit> newUnits,
        List<String> requirements) {

    public boolean hasModifiedFiles() {
        return !diff.modified().isEmpty();
    }

    public boolean hasNewFiles() {
        return !diff.added().isEmpty();
    }

    public int newCount() {
        return diff.added().size();
    }

    public int modifiedCount() {
        return diff.modified().size();
    }

    public int unchangedCount() {
        return diff.unchanged().size();
    }
}

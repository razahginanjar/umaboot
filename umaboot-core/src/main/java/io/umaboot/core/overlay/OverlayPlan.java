package io.umaboot.core.overlay;

import io.umaboot.core.diff.DiffEngine;
import io.umaboot.core.config.ApplicationConfigMerger;
import io.umaboot.core.generator.GeneratedUnit;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Result of planning an overlay generation against an existing project.
 *
 * <p>Overlay mode treats changed existing files as merge work instead of
 * immediately overwriting them.</p>
 */
public record OverlayPlan(
        DiffEngine.DiffResult diff,
        List<GeneratedUnit> newUnits,
        List<GeneratedUnit> previewUnits,
        List<String> requirements,
        BuildFileDependencyPlanner.Plan dependencies,
        ApplicationConfigMerger.Plan applicationConfig) {

    public OverlayPlan {
        newUnits = newUnits == null ? List.of() : List.copyOf(newUnits);
        previewUnits = previewUnits == null ? List.of() : List.copyOf(previewUnits);
        requirements = requirements == null ? List.of() : List.copyOf(requirements);
    }

    public boolean hasModifiedFiles() {
        return !diff.modified().isEmpty();
    }

    public boolean hasNewFiles() {
        return !newUnits.isEmpty();
    }

    public int newCount() {
        return newUnits.size();
    }

    public int modifiedCount() {
        return diff.modified().size();
    }

    public int unchangedCount() {
        return diff.unchanged().size();
    }

    public boolean needsPreviewMerge() {
        return previewMergeCount() > 0;
    }

    public int previewMergeCount() {
        Set<String> patchPaths = new LinkedHashSet<>();
        dependencies.patchUnits().forEach(unit -> patchPaths.add(unit.relativePath()));
        applicationConfig.patchUnits().forEach(unit -> patchPaths.add(unit.relativePath()));
        long previewOnlyAdded = diff.added().stream()
                .filter(patchPaths::contains)
                .count();
        return modifiedCount() + Math.toIntExact(previewOnlyAdded);
    }
}

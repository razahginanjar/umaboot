package io.umaboot.core.diff;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.umaboot.core.generator.GeneratedUnit;

/**
 * Compares newly generated {@link GeneratedUnit}s with whatever is currently on
 * disk and produces a structured {@link DiffResult}.
 */
public final class DiffEngine {

    public DiffResult diff(List<GeneratedUnit> generated, Path outputRoot) {
        Objects.requireNonNull(generated, "generated");
        Objects.requireNonNull(outputRoot, "outputRoot");
        Map<String, FileDiff> perFile = new LinkedHashMap<>();
        List<String> added = new ArrayList<>();
        List<String> modified = new ArrayList<>();
        List<String> unchanged = new ArrayList<>();

        for (GeneratedUnit u : generated) {
            Path target = outputRoot.resolve(u.relativePath().replace('/', java.io.File.separatorChar));
            if (!Files.exists(target)) {
                added.add(u.relativePath());
                perFile.put(u.relativePath(),
                        new FileDiff(u.relativePath(), Status.ADDED, "", u.content(), List.of()));
                continue;
            }
            String current = readUtf8(target);
            if (current.equals(u.content())) {
                unchanged.add(u.relativePath());
                perFile.put(u.relativePath(),
                        new FileDiff(u.relativePath(), Status.UNCHANGED, current, u.content(), List.of()));
                continue;
            }
            List<String> orig = Arrays.asList(current.split("\n", -1));
            List<String> next = Arrays.asList(u.content().split("\n", -1));
            Patch<String> patch = DiffUtils.diff(orig, next);
            List<String> unified = UnifiedDiffUtils.generateUnifiedDiff(
                    u.relativePath(), u.relativePath(), orig, patch, 3);
            modified.add(u.relativePath());
            perFile.put(u.relativePath(),
                    new FileDiff(u.relativePath(), Status.MODIFIED, current, u.content(), unified));
        }

        return new DiffResult(perFile, added, modified, unchanged);
    }

    private static String readUtf8(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + p, e);
        }
    }

    public enum Status { ADDED, MODIFIED, UNCHANGED }

    /**
     * Structured diff for a single file.
     *
     * @param relativePath path relative to outputRoot
     * @param status       added / modified / unchanged
     * @param current      current contents on disk (empty for ADDED)
     * @param generated    newly-generated contents
     * @param unifiedDiff  unified-diff lines (empty when ADDED or UNCHANGED)
     */
    public record FileDiff(String relativePath, Status status,
                           String current, String generated, List<String> unifiedDiff) {}

    /**
     * Aggregated diff across all generated files.
     */
    public record DiffResult(Map<String, FileDiff> perFile,
                             List<String> added,
                             List<String> modified,
                             List<String> unchanged) {

        public boolean hasChanges() {
            return !added.isEmpty() || !modified.isEmpty();
        }
    }
}

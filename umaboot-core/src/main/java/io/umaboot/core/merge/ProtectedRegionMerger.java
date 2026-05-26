package io.umaboot.core.merge;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Preserves user-edited regions when regenerating files.
 *
 * <p>A protected region is delimited by line markers (case-insensitive — both
 * {@code umaboot:protected} and the legacy {@code crudforge:protected} are accepted):</p>
 * <pre>
 *     // &lt;umaboot:protected name="customMethods"&gt;
 *     ... user code lives here ...
 *     // &lt;/umaboot:protected&gt;
 * </pre>
 *
 * <p>For each protected region present in <em>both</em> the existing on-disk
 * file and the newly-generated file (matched by {@code name=...}), the
 * existing block is substituted into the new content. Regions that exist only
 * on disk are dropped. Regions that exist only in the generated file are kept
 * as-is.</p>
 *
 * <p>For Java sources, JavaParser is run on the merged result to validate that
 * it still parses; if not, the merge falls back to the freshly-generated
 * content and the conflict is reported.</p>
 */
public final class ProtectedRegionMerger {

    private static final Logger LOG = LoggerFactory.getLogger(ProtectedRegionMerger.class);

    private static final Pattern OPEN = Pattern.compile(
            "//\\s*<\\s*(?:umaboot|crudforge):protected\\s+name\\s*=\\s*\"([^\"]+)\"\\s*>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CLOSE = Pattern.compile(
            "//\\s*</\\s*(?:umaboot|crudforge):protected\\s*>",
            Pattern.CASE_INSENSITIVE);

    private final JavaParser javaParser = new JavaParser();

    /**
     * Merge {@code existing} into {@code generated}, preserving any matching
     * protected regions from {@code existing}.
     *
     * @return merged content; equal to {@code generated} when no protected
     *         regions match.
     */
    public MergeResult merge(String existing, String generated, boolean isJava) {
        Objects.requireNonNull(existing, "existing");
        Objects.requireNonNull(generated, "generated");

        Map<String, String> existingRegions = collectRegions(existing);
        if (existingRegions.isEmpty()) {
            return new MergeResult(generated, 0, false);
        }

        StringBuilder out = new StringBuilder(generated.length());
        String[] lines = generated.split("\n", -1);
        int i = 0;
        int substituted = 0;
        while (i < lines.length) {
            String line = lines[i];
            Matcher m = OPEN.matcher(line);
            if (m.find()) {
                String name = m.group(1);
                out.append(line);
                if (i < lines.length - 1) out.append('\n');
                // skip until close marker in generated (its body) — substitute existing
                int j = i + 1;
                while (j < lines.length && !CLOSE.matcher(lines[j]).find()) {
                    j++;
                }
                String existingBody = existingRegions.get(name);
                if (existingBody != null) {
                    out.append(existingBody);
                    if (!existingBody.endsWith("\n")) out.append('\n');
                    substituted++;
                } else {
                    // No matching region in existing — keep generated body
                    for (int k = i + 1; k < j; k++) {
                        out.append(lines[k]);
                        if (k < lines.length - 1) out.append('\n');
                    }
                }
                if (j < lines.length) {
                    out.append(lines[j]);
                    if (j < lines.length - 1) out.append('\n');
                }
                i = j + 1;
            } else {
                out.append(line);
                if (i < lines.length - 1) out.append('\n');
                i++;
            }
        }

        String merged = out.toString();
        if (isJava) {
            ParseResult<CompilationUnit> result = javaParser.parse(merged);
            if (!result.isSuccessful()) {
                LOG.warn("Merged result failed to parse — falling back to generated content. " +
                        "Problems: {}", result.getProblems());
                return new MergeResult(generated, 0, true);
            }
        }
        return new MergeResult(merged, substituted, false);
    }

    /** Find protected regions in {@code text} and return them as name -> body. */
    static Map<String, String> collectRegions(String text) {
        Map<String, String> regions = new LinkedHashMap<>();
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            Matcher m = OPEN.matcher(lines[i]);
            if (!m.find()) continue;
            String name = m.group(1);
            int j = i + 1;
            StringBuilder body = new StringBuilder();
            while (j < lines.length && !CLOSE.matcher(lines[j]).find()) {
                body.append(lines[j]);
                if (j < lines.length - 1) body.append('\n');
                j++;
            }
            regions.put(name, body.toString());
            i = j;
        }
        return regions;
    }

    /**
     * @param content    merged file content
     * @param substituted number of regions copied from {@code existing}
     * @param conflict   true when the merge fell back because the result wasn't valid Java
     */
    public record MergeResult(String content, int substituted, boolean conflict) {}
}

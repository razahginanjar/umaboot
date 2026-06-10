package io.umaboot.core.config;

import io.umaboot.core.generator.GeneratorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.umaboot.core.generator.GeneratedUnit;

/**
 * Appends Umaboot-required configuration to an existing project's
 * {@code application.yml}, {@code application.yaml}, or
 * {@code application.properties}.
 *
 * <p>Used in overlay mode where Umaboot does not write its own
 * {@code application.yml}: the user already has one and we don't want to clobber
 * theirs. Instead we append a small marker-bracketed block with the entries
 * that are <em>required</em> for the generated code to work — at minimum, the
 * MyBatis {@code mapper-locations} when {@code persistence=mybatis} with XML
 * mapper files.</p>
 *
 * <p>The merger is idempotent: it looks for the marker comment and refuses to
 * append a second block, so re-running {@code generate} won't keep growing the
 * file.</p>
 */
public final class ApplicationConfigMerger {

    private static final Logger LOG = LoggerFactory.getLogger(ApplicationConfigMerger.class);

    /** Names checked, in priority order. */
    private static final List<String> CANDIDATES = List.of(
            "application.yml",
            "application.yaml",
            "application.properties");

    /** Marker comments — same text, different syntax per format. */
    private static final String MARKER_BEGIN_YAML = "# ---- begin Umaboot additions (do not edit) ----";
    private static final String MARKER_END_YAML   = "# ---- end Umaboot additions ----";
    private static final String MARKER_BEGIN_PROPS = "# ---- begin Umaboot additions (do not edit) ----";
    private static final String MARKER_END_PROPS   = "# ---- end Umaboot additions ----";

    private ApplicationConfigMerger() {}

    /**
     * Append Umaboot's additions to whichever {@code application.*} file exists
     * under {@code outputRoot/src/main/resources/}. If none exists, this is a
     * no-op (the caller is expected to have generated one in standalone mode).
     *
     * @return the file modified, or {@code null} if nothing happened
     */
    public static Path merge(Path outputRoot, GeneratorContext ctx) {
        Objects.requireNonNull(outputRoot, "outputRoot");
        Objects.requireNonNull(ctx, "ctx");

        Map<String, String> additions = computeAdditions(ctx);
        if (additions.isEmpty()) {
            LOG.debug("No application config additions required for {}/{}.",
                    ctx.architecture(), ctx.persistence());
            return null;
        }

        Path resources = outputRoot.resolve("src").resolve("main").resolve("resources");
        Path target = null;
        for (String name : CANDIDATES) {
            Path candidate = resources.resolve(name);
            if (Files.exists(candidate)) {
                target = candidate;
                break;
            }
        }
        if (target == null) {
            LOG.debug("No existing application.{{yml|yaml|properties}} under {} — skip merge.", resources);
            return null;
        }

        try {
            String existing = Files.readString(target, StandardCharsets.UTF_8);
            String marker = target.getFileName().toString().endsWith(".properties")
                    ? MARKER_BEGIN_PROPS
                    : MARKER_BEGIN_YAML;
            if (existing.contains(marker)) {
                LOG.info("Umaboot additions already present in {} — skipping (idempotent).", target);
                return target;
            }
            String snippet = target.getFileName().toString().endsWith(".properties")
                    ? renderProperties(additions)
                    : renderYaml(additions);
            String separator = existing.endsWith("\n") ? "" : "\n";
            Files.writeString(target, existing + separator + "\n" + snippet, StandardCharsets.UTF_8);
            LOG.info("Appended {} Umaboot configuration entries to {}.", additions.size(), target);
            return target;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to merge config into " + target, e);
        }
    }

    /**
     * Build a preview/merge patch for required overlay configuration instead
     * of writing it directly.
     */
    public static Plan plan(Path outputRoot, GeneratorContext ctx) {
        Objects.requireNonNull(outputRoot, "outputRoot");
        Objects.requireNonNull(ctx, "ctx");

        Map<String, String> additions = computeAdditions(ctx);
        if (additions.isEmpty()) {
            return Plan.none();
        }

        Path resources = outputRoot.resolve("src").resolve("main").resolve("resources");
        Path target = null;
        String targetName = null;
        for (String name : CANDIDATES) {
            Path candidate = resources.resolve(name);
            if (Files.exists(candidate)) {
                target = candidate;
                targetName = name;
                break;
            }
        }
        if (target == null) {
            target = resources.resolve("application.yml");
            targetName = "application.yml";
        }

        String relativePath = outputRoot.toAbsolutePath().normalize()
                .relativize(target.toAbsolutePath().normalize())
                .toString()
                .replace(java.io.File.separatorChar, '/');
        boolean properties = targetName.endsWith(".properties");
        String marker = properties ? MARKER_BEGIN_PROPS : MARKER_BEGIN_YAML;
        String snippet = properties ? renderProperties(additions) : renderYaml(additions);
        List<String> messages = new ArrayList<>();

        try {
            String patched;
            if (!Files.exists(target)) {
                patched = snippet;
                messages.add("Application config patch available in Preview / Merge: "
                        + relativePath + " (new file)");
            } else {
                String existing = Files.readString(target, StandardCharsets.UTF_8);
                if (existing.contains(marker)) {
                    return new Plan(target, relativePath, true, false,
                            List.of(), List.of("Application config already contains Umaboot additions: " + relativePath));
                }
                String separator = existing.endsWith("\n") ? "" : "\n";
                patched = existing + separator + "\n" + snippet;
                messages.add("Application config patch available in Preview / Merge: " + relativePath);
            }
            return new Plan(target, relativePath, true, true,
                    List.of(new GeneratedUnit(relativePath, patched)), messages);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to plan application config patch for " + target, e);
        }
    }

    /**
     * Decide which key/value entries the generated code needs at runtime.
     * Returns a flat dotted-key map (e.g. {@code mybatis.mapper-locations}).
     */
    static Map<String, String> computeAdditions(GeneratorContext ctx) {
        Map<String, String> a = new LinkedHashMap<>();
        if (ctx.isMyBatis() && ctx.myBatisXml()) {
            a.put("mybatis.mapper-locations", "classpath:mapper/*.xml");
            a.put("mybatis.configuration.map-underscore-to-camel-case", "true");
        } else if (ctx.isMyBatis()) {
            a.put("mybatis.configuration.map-underscore-to-camel-case", "true");
        }
        return a;
    }

    /** Render flat dotted keys as a YAML block, expanding levels. */
    static String renderYaml(Map<String, String> entries) {
        // Build a nested map first, then walk it to produce indented YAML.
        Map<String, Object> tree = new LinkedHashMap<>();
        for (var e : entries.entrySet()) {
            String[] parts = e.getKey().split("\\.");
            @SuppressWarnings("unchecked")
            Map<String, Object> node = tree;
            for (int i = 0; i < parts.length - 1; i++) {
                Object child = node.get(parts[i]);
                if (!(child instanceof Map)) {
                    Map<String, Object> next = new LinkedHashMap<>();
                    node.put(parts[i], next);
                    node = next;
                } else {
                    node = (Map<String, Object>) child;
                }
            }
            node.put(parts[parts.length - 1], e.getValue());
        }

        StringBuilder out = new StringBuilder();
        out.append(MARKER_BEGIN_YAML).append('\n');
        writeYaml(out, tree, 0);
        out.append(MARKER_END_YAML).append('\n');
        return out.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeYaml(StringBuilder out, Map<String, Object> node, int indent) {
        String pad = "  ".repeat(indent);
        for (var e : node.entrySet()) {
            if (e.getValue() instanceof Map<?, ?> m) {
                out.append(pad).append(e.getKey()).append(":\n");
                writeYaml(out, (Map<String, Object>) m, indent + 1);
            } else {
                out.append(pad).append(e.getKey()).append(": ").append(e.getValue()).append('\n');
            }
        }
    }

    static String renderProperties(Map<String, String> entries) {
        StringBuilder out = new StringBuilder();
        out.append(MARKER_BEGIN_PROPS).append('\n');
        for (var e : entries.entrySet()) {
            out.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        out.append(MARKER_END_PROPS).append('\n');
        return out.toString();
    }

    public record Plan(Path target, String relativePath, boolean required, boolean hasPatch,
                       List<GeneratedUnit> patchUnits, List<String> messages) {

        public Plan {
            patchUnits = patchUnits == null ? List.of() : List.copyOf(patchUnits);
            messages = messages == null ? List.of() : List.copyOf(messages);
        }

        static Plan none() {
            return new Plan(null, "", false, false, List.of(), List.of());
        }
    }
}

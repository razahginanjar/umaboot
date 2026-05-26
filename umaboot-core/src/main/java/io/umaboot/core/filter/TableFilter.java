package io.umaboot.core.filter;

import io.umaboot.core.model.RelationshipModel;
import io.umaboot.core.model.SchemaModel;
import io.umaboot.core.model.TableModel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Applies include / exclude glob filters to a {@link SchemaModel}.
 *
 * <p>After filtering, any relationship pointing at a removed table is dropped.
 * Junction tables that link two surviving tables are preserved (so the
 * remaining tables still see their {@code @ManyToMany} relationships); a
 * junction whose endpoints were filtered out is dropped along with both sides
 * of the link.</p>
 *
 * <p>Patterns use shell-style globs: {@code *} = any characters,
 * {@code ?} = single character. Matching is case-insensitive.</p>
 */
public final class TableFilter {

    private final List<Pattern> include;
    private final List<Pattern> exclude;

    public TableFilter(List<String> includeGlobs, List<String> excludeGlobs) {
        Objects.requireNonNull(includeGlobs, "includeGlobs");
        Objects.requireNonNull(excludeGlobs, "excludeGlobs");
        this.include = compileAll(includeGlobs);
        this.exclude = compileAll(excludeGlobs);
    }

    /**
     * Convenience: no filtering — every table passes through.
     */
    public static TableFilter allowAll() {
        return new TableFilter(List.of(), List.of());
    }

    /**
     * @return true if the table name passes both filters.
     */
    public boolean accepts(String tableName) {
        Objects.requireNonNull(tableName, "tableName");
        String lower = tableName.toLowerCase(Locale.ROOT);
        // include: empty list means "match everything"
        boolean included = include.isEmpty() || include.stream().anyMatch(p -> p.matcher(lower).matches());
        if (!included) return false;
        // exclude: any match removes the table
        return exclude.stream().noneMatch(p -> p.matcher(lower).matches());
    }

    /**
     * Apply filtering to a schema. Tables that fail the predicate are dropped;
     * relationships pointing to dropped tables are also pruned.
     */
    public SchemaModel apply(SchemaModel schema) {
        Objects.requireNonNull(schema, "schema");

        Set<String> kept = new LinkedHashSet<>();
        for (TableModel t : schema.tables()) {
            if (accepts(t.name())) kept.add(t.name().toLowerCase(Locale.ROOT));
        }

        // Junction tables survive only if both endpoints are kept; if a
        // junction is kept by accept() but its endpoints aren't, it would emit
        // dangling references — strip such junctions.
        List<TableModel> filtered = new ArrayList<>();
        for (TableModel t : schema.tables()) {
            if (!kept.contains(t.name().toLowerCase(Locale.ROOT))) continue;
            if (t.junction()) {
                boolean endpointsSurvive = t.relationships().stream()
                        .filter(RelationshipModel::owning)
                        .allMatch(r -> kept.contains(r.toTable().toLowerCase(Locale.ROOT)));
                if (!endpointsSurvive) continue;
            }
            filtered.add(prunedRelationships(t, kept));
        }
        return new SchemaModel(schema.schemaName(), filtered);
    }

    private static TableModel prunedRelationships(TableModel t, Set<String> kept) {
        List<RelationshipModel> survived = new ArrayList<>();
        for (RelationshipModel r : t.relationships()) {
            if (kept.contains(r.toTable().toLowerCase(Locale.ROOT))) {
                survived.add(r);
            }
        }
        if (survived.size() == t.relationships().size()) return t;
        return t.withRelationships(survived);
    }

    private static List<Pattern> compileAll(List<String> globs) {
        List<Pattern> out = new ArrayList<>(globs.size());
        for (String glob : globs) out.add(globToRegex(glob));
        return List.copyOf(out);
    }

    static Pattern globToRegex(String glob) {
        StringBuilder rx = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> rx.append(".*");
                case '?' -> rx.append('.');
                case '.', '(', ')', '+', '|', '^', '$', '@', '%' -> rx.append('\\').append(c);
                case '\\' -> rx.append("\\\\");
                default -> rx.append(Character.toLowerCase(c));
            }
        }
        rx.append('$');
        return Pattern.compile(rx.toString());
    }
}

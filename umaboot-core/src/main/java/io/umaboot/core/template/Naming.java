package io.umaboot.core.template;

import java.util.Locale;

/**
 * Naming conversion helpers — snake_case &lt;-&gt; PascalCase / camelCase.
 *
 * <p>Used by generators to derive class names ({@code customer_orders -&gt; CustomerOrder})
 * and field names ({@code first_name -&gt; firstName}) from raw SQL identifiers.</p>
 */
public final class Naming {

    private Naming() {}

    /** snake_case or kebab-case to PascalCase, with naive singularization. */
    public static String toPascalCase(String snake) {
        return capitalize(toCamelCase(snake));
    }

    /** snake_case or kebab-case to camelCase. */
    public static String toCamelCase(String snake) {
        if (snake == null || snake.isEmpty()) return snake;
        StringBuilder sb = new StringBuilder(snake.length());
        boolean upperNext = false;
        for (int i = 0; i < snake.length(); i++) {
            char c = snake.charAt(i);
            if (c == '_' || c == '-' || c == ' ') {
                upperNext = true;
            } else if (upperNext) {
                sb.append(Character.toUpperCase(c));
                upperNext = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    /** Naive English singularization for class names. */
    public static String singularize(String name) {
        if (name == null || name.length() < 2) return name;
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith("ies") && name.length() > 3) {
            return name.substring(0, name.length() - 3) + "y";
        }
        if (lower.endsWith("ses") || lower.endsWith("xes") || lower.endsWith("zes")
                || lower.endsWith("ches") || lower.endsWith("shes")) {
            return name.substring(0, name.length() - 2);
        }
        // Common non-plural endings — leave alone.
        if (lower.endsWith("ss") || lower.endsWith("us")
                || lower.endsWith("is") || lower.endsWith("os")) {
            return name;
        }
        if (lower.endsWith("s")) {
            return name.substring(0, name.length() - 1);
        }
        return name;
    }

    public static String entityClass(String tableName) {
        return toPascalCase(singularize(tableName));
    }

    public static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}

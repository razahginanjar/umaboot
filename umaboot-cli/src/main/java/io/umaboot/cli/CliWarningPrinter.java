package io.umaboot.cli;

import java.util.List;

final class CliWarningPrinter {

    private CliWarningPrinter() {}

    static void printSchemaWarnings(List<String> warnings) {
        if (warnings == null || warnings.isEmpty()) return;
        if (warnings.size() == 1) {
            System.err.println("Schema introspection warning: " + warnings.get(0));
            return;
        }
        System.err.println("Schema introspection produced " + warnings.size() + " warning(s).");
        System.err.println("First schema warning: " + warnings.get(0));
    }

    static void printParserWarnings(List<String> warnings) {
        printSchemaWarnings(warnings);
    }
}

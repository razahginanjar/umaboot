package io.umaboot.cli;

import java.util.List;

final class CliWarningPrinter {

    private CliWarningPrinter() {}

    static void printParserWarnings(List<String> warnings) {
        if (warnings == null || warnings.isEmpty()) return;
        System.err.println("Schema parsed with " + warnings.size() + " warning(s).");
        System.err.println("First parser warning: " + warnings.get(0));
    }
}

package io.umaboot.core.generator;

import java.util.Objects;

/**
 * A single generated file: a relative path within the output project plus
 * its full text content.
 *
 * @param relativePath path relative to the output project root (forward-slash separated)
 * @param content      the rendered file contents
 */
public record GeneratedUnit(String relativePath, String content) {
    public GeneratedUnit {
        Objects.requireNonNull(relativePath, "relativePath");
        Objects.requireNonNull(content, "content");
    }
}

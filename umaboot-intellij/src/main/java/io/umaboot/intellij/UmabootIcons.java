package io.umaboot.intellij;

import com.intellij.openapi.util.IconLoader;

import javax.swing.Icon;

/**
 * Centralized icon constants for the plugin. All icons live under
 * {@code resources/icons/} and are loaded via {@link IconLoader} so the IDE
 * can theme / scale them automatically.
 */
public final class UmabootIcons {

    /** 16x16 — Tools menu, toolbars, generic action buttons. */
    public static final Icon ACTION = IconLoader.getIcon("/icons/umaboot.png", UmabootIcons.class);

    /** 13x13 — right-gutter tool-window stripe. */
    public static final Icon TOOL_WINDOW = IconLoader.getIcon("/icons/umaboot_toolwindow.png", UmabootIcons.class);

    /** 16x16 — file-type icon for {@code umaboot.yaml}. */
    public static final Icon FILE = IconLoader.getIcon("/icons/umaboot_file.png", UmabootIcons.class);

    private UmabootIcons() {}
}

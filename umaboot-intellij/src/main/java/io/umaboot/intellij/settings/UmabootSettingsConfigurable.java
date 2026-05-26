package io.umaboot.intellij.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;

/**
 * Hosts the {@link UmabootSettingsPanel} under
 * {@code Settings → Tools → Umaboot}.
 */
public final class UmabootSettingsConfigurable implements Configurable {

    private final Project project;
    private UmabootSettingsPanel panel;

    public UmabootSettingsConfigurable(Project project) {
        this.project = project;
    }

    @Override
    public @Nls String getDisplayName() {
        return "Umaboot";
    }

    @Override
    public @Nullable JComponent createComponent() {
        if (panel == null) panel = new UmabootSettingsPanel(project);
        return panel.getRoot();
    }

    @Override
    public boolean isModified() {
        return panel != null && panel.isModified();
    }

    @Override
    public void apply() throws ConfigurationException {
        if (panel == null) return;
        try {
            panel.save();
        } catch (Exception ex) {
            throw new ConfigurationException("Failed to save umaboot.yaml: " + ex.getMessage());
        }
    }

    @Override
    public void reset() {
        if (panel != null) panel.load();
    }

    @Override
    public void disposeUIResources() {
        panel = null;
    }
}

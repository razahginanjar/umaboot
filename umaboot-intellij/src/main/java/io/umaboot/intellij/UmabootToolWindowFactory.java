package io.umaboot.intellij;

import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import io.umaboot.intellij.settings.UmabootSettingsPanel;
import io.umaboot.intellij.settings.UiText;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import java.awt.BorderLayout;

/**
 * Right-gutter tool window. Hosts the same {@link UmabootSettingsPanel} that
 * lives under Settings → Tools → Umaboot, with explicit
 * <em>Apply</em>, <em>Generate</em>, and <em>Preview / Merge</em> actions so
 * users discover and use the form without having to know about the Settings
 * dialog.
 */
public final class UmabootToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        toolWindow.setIcon(UmabootIcons.TOOL_WINDOW);

        UmabootSettingsPanel panel = new UmabootSettingsPanel(project);

        JBPanel<JBPanel<?>> root = new JBPanel<>(new BorderLayout());
        root.add(buildHeaderBar(project, panel), BorderLayout.NORTH);
        root.add(panel.getRoot(), BorderLayout.CENTER);
        root.add(buildWorkflowBar(project, panel), BorderLayout.SOUTH);

        Content content = ContentFactory.getInstance().createContent(
                root,
                UiText.text(UiText.load(project), "Configuration"),
                false);
        toolWindow.getContentManager().addContent(content);
    }

    private JBPanel<JBPanel<?>> buildHeaderBar(Project project, UmabootSettingsPanel panel) {
        JBPanel<JBPanel<?>> bar = new JBPanel<>(new BorderLayout());
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new java.awt.Color(0xC0C0C0)),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        DefaultActionGroup actions = new DefaultActionGroup();
        actions.add(toolbarAction(project, "Revert", "Restore the form to the last loaded YAML state",
                AllIcons.Actions.Rollback, () -> revertForm(project, panel, UiText.load(project))));
        actions.add(toolbarAction(project, "Sync", "Reload the form from umaboot.yaml without saving",
                AllIcons.Actions.Refresh, () -> syncFromYaml(project, panel, UiText.load(project))));
        actions.add(logsActionGroup(project));

        ActionToolbar toolbar = ActionManager.getInstance()
                .createActionToolbar("Umaboot.ToolWindow.Header", actions, true);
        toolbar.setTargetComponent(bar);
        panel.addLanguageChangeListener(selected -> toolbar.updateActionsImmediately());
        bar.add(toolbar.getComponent(), BorderLayout.CENTER);
        return bar;
    }

    private JBPanel<JBPanel<?>> buildWorkflowBar(Project project, UmabootSettingsPanel panel) {
        JBPanel<JBPanel<?>> bar = new JBPanel<>(new BorderLayout());
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new java.awt.Color(0xC0C0C0)),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        DefaultActionGroup actions = new DefaultActionGroup();
        actions.add(toolbarAction(project, "Apply", "Write the form values back to umaboot.yaml",
                AllIcons.Actions.MenuSaveall, () -> {
            try {
                panel.save();
                notifyUser(project, UiText.text(UiText.load(project), "Saved umaboot.yaml"), NotificationType.INFORMATION);
            } catch (Exception ex) {
                notifyUser(project, UiText.text(UiText.load(project), "Failed to save: ") + ex.getMessage(), NotificationType.ERROR);
            }
        }));
        actions.add(toolbarAction(project, "Generate", "Run Umaboot against the current configuration",
                UmabootIcons.ACTION, () -> runGenerateAction(project)));
        actions.add(toolbarAction(project, "Preview / Merge", "Preview generated changes before writing files",
                AllIcons.Actions.Diff, () -> runPreviewMergeAction(project)));

        ActionToolbar toolbar = ActionManager.getInstance()
                .createActionToolbar("Umaboot.ToolWindow.Workflow", actions, true);
        toolbar.setTargetComponent(bar);
        panel.addLanguageChangeListener(selected -> toolbar.updateActionsImmediately());
        bar.add(toolbar.getComponent(), BorderLayout.CENTER);
        return bar;
    }

    private static AnAction toolbarAction(Project project, String textKey, String descriptionKey,
                                          Icon icon, Runnable handler) {
        return new AnAction(UiText.text(UiText.load(project), textKey),
                UiText.text(UiText.load(project), descriptionKey),
                icon) {
            @Override
            public void update(AnActionEvent e) {
                UiText.Language language = UiText.load(project);
                e.getPresentation().setText(UiText.text(language, textKey));
                e.getPresentation().setDescription(UiText.text(language, descriptionKey));
                e.getPresentation().setIcon(icon);
            }

            @Override
            public void actionPerformed(AnActionEvent e) {
                handler.run();
            }
        };
    }

    private static DefaultActionGroup logsActionGroup(Project project) {
        DefaultActionGroup group = new DefaultActionGroup(UiText.text(UiText.load(project), "Logs"), true) {
            @Override
            public void update(AnActionEvent e) {
                UiText.Language language = UiText.load(project);
                e.getPresentation().setText(UiText.text(language, "Logs"));
                e.getPresentation().setDescription(UiText.text(language, "Open Umaboot logs"));
                e.getPresentation().setIcon(AllIcons.General.Information);
            }
        };
        group.getTemplatePresentation().setIcon(AllIcons.General.Information);
        group.add(toolbarAction(project, "Summary Log", "Show concise Umaboot process log",
                AllIcons.General.Information, () -> UmabootLog.get(project).showSummary()));
        group.add(toolbarAction(project, "Detail Log", "Show detailed Umaboot process log",
                AllIcons.General.Information, () -> UmabootLog.get(project).showDetail()));
        return group;
    }

    private static void revertForm(Project project, UmabootSettingsPanel panel, UiText.Language language) {
        if (!panel.isModified()) {
            notifyUser(project, UiText.text(language, "No form changes to revert"), NotificationType.INFORMATION);
            return;
        }
        int choice = Messages.showYesNoDialog(
                project,
                UiText.text(language, "Discard unsaved form changes?"),
                UiText.text(language, "Revert"),
                Messages.getWarningIcon());
        if (choice != Messages.YES) return;
        panel.revert();
        notifyUser(project, UiText.text(language, "Reverted form changes"), NotificationType.INFORMATION);
    }

    private static void syncFromYaml(Project project, UmabootSettingsPanel panel, UiText.Language language) {
        if (panel.isModified()) {
            int choice = Messages.showYesNoDialog(
                    project,
                    UiText.text(language, "Discard unsaved form changes and reload from YAML?"),
                    UiText.text(language, "Sync from YAML"),
                    Messages.getWarningIcon());
            if (choice != Messages.YES) return;
        }
        try {
            panel.syncFromYaml();
            notifyUser(project, UiText.text(language, "Synced from YAML"), NotificationType.INFORMATION);
        } catch (Exception ex) {
            notifyUser(project,
                    UiText.text(language, "Failed to sync from YAML: ") + ex.getMessage(),
                    NotificationType.ERROR);
        }
    }

    private static void runGenerateAction(Project project) {
        var action = ActionManager.getInstance().getAction("Umaboot.Generate");
        if (action == null) return;
        DataContext ctx = SimpleDataContext.getProjectContext(project);
        AnActionEvent event = AnActionEvent.createFromDataContext("UmabootToolWindow", new Presentation(), ctx);
        action.actionPerformed(event);
    }

    private static void runPreviewMergeAction(Project project) {
        var action = ActionManager.getInstance().getAction("Umaboot.PreviewMerge");
        if (action == null) return;
        DataContext ctx = SimpleDataContext.getProjectContext(project);
        AnActionEvent event = AnActionEvent.createFromDataContext("UmabootToolWindow", new Presentation(), ctx);
        action.actionPerformed(event);
    }

    private static void notifyUser(Project project, String message, NotificationType type) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("Umaboot")
                .createNotification(message, type)
                .notify(project);
    }
}

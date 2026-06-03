package io.umaboot.intellij;

import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import io.umaboot.intellij.settings.UmabootSettingsConfigurable;
import io.umaboot.intellij.settings.UmabootSettingsPanel;
import io.umaboot.intellij.settings.UiText;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import java.awt.BorderLayout;
import java.awt.FlowLayout;

/**
 * Right-gutter tool window. Hosts the same {@link UmabootSettingsPanel} that
 * lives under Settings → Tools → Umaboot, with explicit
 * <em>Apply</em>, <em>Generate</em>, and <em>Open Settings</em> buttons so
 * users discover and use the form without having to know about the Settings
 * dialog.
 */
public final class UmabootToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        toolWindow.setIcon(UmabootIcons.TOOL_WINDOW);

        UmabootSettingsPanel panel = new UmabootSettingsPanel(project);

        JBPanel<JBPanel<?>> root = new JBPanel<>(new BorderLayout());
        root.add(panel.getRoot(), BorderLayout.CENTER);
        root.add(buildButtonBar(project, panel), BorderLayout.SOUTH);

        Content content = ContentFactory.getInstance().createContent(
                root,
                UiText.text(UiText.load(project), "Configuration"),
                false);
        toolWindow.getContentManager().addContent(content);
    }

    private JBPanel<JBPanel<?>> buildButtonBar(Project project, UmabootSettingsPanel panel) {
        final UiText.Language[] language = {UiText.load(project)};
        JBPanel<JBPanel<?>> bar = new JBPanel<>(new FlowLayout(FlowLayout.LEFT, 6, 6));
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new java.awt.Color(0xC0C0C0)));

        JButton apply = new JButton(UiText.text(language[0], "Apply"), AllIcons.Actions.MenuSaveall);
        apply.setToolTipText(UiText.text(language[0], "Write the form values back to umaboot.yaml"));
        apply.addActionListener(e -> {
            try {
                panel.save();
                notifyUser(project, UiText.text(language[0], "Saved umaboot.yaml"), NotificationType.INFORMATION);
            } catch (Exception ex) {
                notifyUser(project, UiText.text(language[0], "Failed to save: ") + ex.getMessage(), NotificationType.ERROR);
            }
        });

        JButton generate = new JButton(UiText.text(language[0], "Generate"), UmabootIcons.ACTION);
        generate.setToolTipText(UiText.text(language[0], "Run Umaboot against the current configuration"));
        generate.addActionListener(e -> runGenerateAction(project));

        JButton previewMerge = new JButton(UiText.text(language[0], "Preview / Merge"), AllIcons.Actions.Diff);
        previewMerge.setToolTipText(UiText.text(language[0], "Preview generated changes before writing files"));
        previewMerge.addActionListener(e -> runPreviewMergeAction(project));

        JButton summaryLog = new JButton(UiText.text(language[0], "Summary Log"), AllIcons.General.Information);
        summaryLog.setToolTipText(UiText.text(language[0], "Show concise Umaboot process log"));
        summaryLog.addActionListener(e -> UmabootLog.get(project).showSummary());

        JButton detailLog = new JButton(UiText.text(language[0], "Detail Log"), AllIcons.General.Information);
        detailLog.setToolTipText(UiText.text(language[0], "Show detailed Umaboot process log"));
        detailLog.addActionListener(e -> UmabootLog.get(project).showDetail());

        JButton openSettings = new JButton(UiText.text(language[0], "Open in Settings"), AllIcons.General.Settings);
        openSettings.setToolTipText(UiText.text(language[0], "Open this panel inside the IDE Settings dialog"));
        openSettings.addActionListener(e ->
                ShowSettingsUtil.getInstance().showSettingsDialog(project, UmabootSettingsConfigurable.class));

        panel.addLanguageChangeListener(selected -> {
            language[0] = selected;
            apply.setText(UiText.text(selected, "Apply"));
            apply.setToolTipText(UiText.text(selected, "Write the form values back to umaboot.yaml"));
            generate.setText(UiText.text(selected, "Generate"));
            generate.setToolTipText(UiText.text(selected, "Run Umaboot against the current configuration"));
            previewMerge.setText(UiText.text(selected, "Preview / Merge"));
            previewMerge.setToolTipText(UiText.text(selected, "Preview generated changes before writing files"));
            summaryLog.setText(UiText.text(selected, "Summary Log"));
            summaryLog.setToolTipText(UiText.text(selected, "Show concise Umaboot process log"));
            detailLog.setText(UiText.text(selected, "Detail Log"));
            detailLog.setToolTipText(UiText.text(selected, "Show detailed Umaboot process log"));
            openSettings.setText(UiText.text(selected, "Open in Settings"));
            openSettings.setToolTipText(UiText.text(selected, "Open this panel inside the IDE Settings dialog"));
        });

        bar.add(apply);
        bar.add(generate);
        bar.add(previewMerge);
        bar.add(summaryLog);
        bar.add(detailLog);
        bar.add(openSettings);
        return bar;
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

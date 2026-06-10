package io.umaboot.intellij;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import io.umaboot.intellij.settings.UiText;

public final class ResetPreviewMergeAction extends AnAction {

    public ResetPreviewMergeAction() {
        super(UiText.text(UiText.Language.ENGLISH, "Umaboot: Reset Preview / Merge"),
                UiText.text(UiText.Language.ENGLISH, "Discard active Preview / Merge choices without touching files"),
                UmabootIcons.ACTION);
    }

    @Override
    public ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(AnActionEvent e) {
        Project project = e.getProject();
        UiText.Language language = UiText.load(project);
        e.getPresentation().setText(UiText.text(language, "Umaboot: Reset Preview / Merge"));
        e.getPresentation().setDescription(
                UiText.text(language, "Discard active Preview / Merge choices without touching files"));
        e.getPresentation().setEnabled(project != null);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        UiText.Language language = UiText.load(project);
        if (project == null) {
            notifyUser(null, UiText.text(language, "Umaboot: open a project first."), NotificationType.ERROR);
            return;
        }

        PreviewMergeState state = PreviewMergeState.get(project);
        if (!state.hasActiveSession()) {
            notifyUser(project,
                    UiText.text(language, "No active Preview / Merge choices to reset."),
                    NotificationType.INFORMATION);
            return;
        }

        int choice = Messages.showYesNoDialog(
                project,
                UiText.text(language, "Reset active Preview / Merge choices? This does not touch files already written."),
                UiText.text(language, "Umaboot: Reset Preview / Merge"),
                UiText.text(language, "Reset"),
                UiText.text(language, "Cancel"),
                Messages.getWarningIcon());
        if (choice != Messages.YES) return;

        int count = state.reset();
        UmabootLog.get(project).event("preview merge", "reset active session with " + count + " file(s)");
        notifyUser(project,
                UiText.text(language, "Preview / Merge choices reset."),
                NotificationType.INFORMATION);
    }

    private static void notifyUser(Project project, String message, NotificationType type) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("Umaboot")
                .createNotification(message, type)
                .notify(project);
    }
}

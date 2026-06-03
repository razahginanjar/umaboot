package io.umaboot.intellij;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import io.umaboot.intellij.settings.UiText;

public final class ShowDetailLogAction extends AnAction {

    public ShowDetailLogAction() {
        super(UiText.text(UiText.Language.ENGLISH, "Umaboot: Show Detail Log"),
                UiText.text(UiText.Language.ENGLISH, "Show detailed Umaboot process log"),
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
        e.getPresentation().setText(UiText.text(language, "Umaboot: Show Detail Log"));
        e.getPresentation().setDescription(UiText.text(language, "Show detailed Umaboot process log"));
        e.getPresentation().setEnabled(project != null);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project != null) {
            UmabootLog.get(project).showDetail();
        }
    }
}

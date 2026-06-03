package io.umaboot.intellij;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import io.umaboot.intellij.settings.UiText;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tools menu / toolbar entry: looks for {@code umaboot.yaml} in the project
 * root, runs {@link UmabootRunner}, and refreshes the VFS so the generated
 * files appear in the Project view.
 *
 * <p>Carries the Umaboot icon and is automatically grayed out when the
 * current project has no {@code umaboot.yaml}.</p>
 */
public final class GenerateAction extends AnAction {

    public GenerateAction() {
        super(UiText.text(UiText.Language.ENGLISH, "Umaboot: Generate"),
                UiText.text(UiText.Language.ENGLISH, "Run Umaboot against the project's umaboot.yaml"),
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
        boolean enabled = project != null
                && project.getBasePath() != null
                && Files.exists(UmabootConfigLocator.findConfigFile(Path.of(project.getBasePath())));
        e.getPresentation().setText(UiText.text(language, "Umaboot: Generate"));
        e.getPresentation().setEnabled(enabled);
        e.getPresentation().setDescription(enabled
                ? UiText.text(language, "Run Umaboot against this project's umaboot.yaml")
                : UiText.text(language, "Add a umaboot.yaml to the project root to enable"));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        UiText.Language language = UiText.load(project);
        if (project == null || project.getBasePath() == null) {
            notifyUser(null, UiText.text(language, "Umaboot: open a project first."), NotificationType.ERROR);
            return;
        }

        Path configPath = UmabootConfigLocator.findConfigFile(Path.of(project.getBasePath()));
        new Task.Backgroundable(project, UiText.text(language, "Umaboot: Generating"), true) {
            @Override
            public void run(ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                indicator.setText(UiText.text(language, "Introspecting database and rendering project..."));

                try {
                    UmabootRunner.Result result = new UmabootRunner().run(configPath);

                    ApplicationManager.getApplication().invokeLater(() -> {
                        VirtualFile vf = LocalFileSystem.getInstance()
                                .refreshAndFindFileByPath(result.outputDir().toString());
                        if (vf != null) vf.refresh(true, true);
                    });

                    notifyUser(project,
                            UiText.format(language, "Umaboot: generated %d files in %s [%s/%s, %s%s]",
                                    result.fileCount(),
                                    result.outputDir(),
                                    result.architecture(),
                                    result.persistence(),
                                    result.mode(),
                                    result.autoOverlay() ? UiText.text(language, " (auto)") : ""),
                            NotificationType.INFORMATION);
                } catch (Exception ex) {
                    notifyUser(project, UiText.format(language, "Umaboot failed: %s", ex.getMessage()), NotificationType.ERROR);
                }
            }
        }.queue();
    }

    /**
     * Wraps the IntelliJ notification API. Renamed from {@code notify} to
     * avoid colliding with {@link Object#notify()} (which is in scope inside
     * anonymous inner classes and would shadow this method).
     */
    private static void notifyUser(Project project, String message, NotificationType type) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("Umaboot")
                .createNotification(message, type)
                .notify(project);
    }
}

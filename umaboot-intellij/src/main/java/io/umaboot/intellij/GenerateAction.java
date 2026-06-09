package io.umaboot.intellij;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import io.umaboot.core.standalone.StandaloneOutputSafety;
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

    private static final Logger LOG = Logger.getInstance(GenerateAction.class);

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
        String existingPolicyOverride = null;
        try {
            StandaloneOutputSafety.Plan safety = new UmabootRunner().inspectStandaloneOutput(configPath);
            if (safety != null && safety.shouldBlock() && "warn".equals(safety.existingPolicy())) {
                int choice = Messages.showDialog(
                        project,
                        "Standalone output already looks like an existing project:\n"
                                + safety.outputDir()
                                + "\n\nMarkers: " + safety.markerSummary()
                                + "\n\nOverwrite replaces generated file paths. Clean Output deletes the output contents before generation.",
                        "Umaboot: Standalone Output Exists",
                        new String[]{"Overwrite", "Clean Output", "Cancel"},
                        0,
                        Messages.getWarningIcon());
                if (choice == 0) {
                    existingPolicyOverride = "overwrite";
                } else if (choice == 1) {
                    existingPolicyOverride = "clean";
                } else {
                    return;
                }
            }
        } catch (Exception ex) {
            notifyUser(project,
                    UiText.format(language, "Umaboot failed: %s. See detail log for diagnostics.",
                            UmabootLog.rootMessage(ex)),
                    NotificationType.ERROR);
            return;
        }
        String finalExistingPolicyOverride = existingPolicyOverride;
        UmabootLog log = UmabootLog.get(project);
        log.started("generate", "config: " + configPath);
        new Task.Backgroundable(project, UiText.text(language, "Umaboot: Generating"), true) {
            @Override
            public void run(ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                indicator.setText(UiText.text(language, "Introspecting database and rendering project..."));

                try {
                    UmabootRunner.Result result =
                            new UmabootRunner().run(configPath, finalExistingPolicyOverride);
                    log.detail("generate", "outputDir: " + result.outputDir());
                    log.detail("generate", "architecture: " + result.architecture());
                    log.detail("generate", "persistence: " + result.persistence());
                    log.detail("generate", "mode: " + result.mode() + (result.autoOverlay() ? " (auto)" : ""));
                    if ("overlay".equals(result.mode())) {
                        log.detail("generate", "overlay new files: " + result.fileCount());
                        log.detail("generate", "overlay modified files: " + result.overlayModifiedCount());
                        log.detail("generate", "overlay unchanged files: " + result.overlayUnchangedCount());
                        for (String file : result.overlayModifiedFiles()) {
                            log.detail("generate", "overlay merge required: " + file);
                        }
                        for (String requirement : result.overlayRequirements()) {
                            log.detail("generate", "overlay requirement: " + requirement);
                        }
                    }
                    log.finished("generate", "generated " + result.fileCount() + " files");

                    ApplicationManager.getApplication().invokeLater(() -> {
                        VirtualFile vf = LocalFileSystem.getInstance()
                                .refreshAndFindFileByPath(result.outputDir().toString());
                        if (vf != null) vf.refresh(true, true);
                    });

                    NotificationType generationType = result.overlayModifiedCount() > 0
                            ? NotificationType.WARNING
                            : NotificationType.INFORMATION;
                    String generationMessage = result.overlayModifiedCount() > 0
                            ? UiText.format(language,
                                    "Umaboot: overlay wrote %d new files; %d modified files need Preview / Merge. See detail log.",
                                    result.fileCount(),
                                    result.overlayModifiedCount())
                            : UiText.format(language, "Umaboot: generated %d files in %s [%s/%s, %s%s]",
                                    result.fileCount(),
                                    result.outputDir(),
                                    result.architecture(),
                                    result.persistence(),
                                    result.mode(),
                                    result.autoOverlay() ? UiText.text(language, " (auto)") : "");
                    notifyUser(project, generationMessage, generationType);
                    if (!result.warnings().isEmpty()) {
                        log.parserWarnings("generate", result.warnings());
                        LOG.warn("Umaboot generated with parser warnings: " + result.warnings());
                        notifyUser(project,
                                UiText.format(language,
                                        "Umaboot: generated with %d parser warning(s). First: %s",
                                        result.warnings().size(),
                                        result.warnings().get(0)),
                                NotificationType.WARNING);
                    }
                } catch (Exception ex) {
                    log.failed("generate", ex);
                    log.showDetail();
                    LOG.warn("Umaboot generation failed", ex);
                    notifyUser(project,
                            UiText.format(language, "Umaboot failed: %s. See detail log for diagnostics.",
                                    UmabootLog.rootMessage(ex)),
                            NotificationType.ERROR);
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

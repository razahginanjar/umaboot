package io.umaboot.intellij;

import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestFactory;
import com.intellij.diff.InvalidDiffRequestException;
import com.intellij.diff.merge.MergeRequest;
import com.intellij.diff.merge.MergeResult;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import io.umaboot.core.generator.GeneratedUnit;
import io.umaboot.core.overlay.OverlayPlan;
import io.umaboot.core.overlay.OverlayPlanner;
import io.umaboot.intellij.settings.UiText;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates output in memory and lets the user merge each changed file through
 * IntelliJ's built-in merge viewer before anything is written.
 */
public final class PreviewMergeAction extends AnAction {

    public PreviewMergeAction() {
        super(UiText.text(UiText.Language.ENGLISH, "Umaboot: Preview / Merge"),
                UiText.text(UiText.Language.ENGLISH, "Preview generated changes against existing files"),
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
        e.getPresentation().setText(UiText.text(language, "Umaboot: Preview / Merge"));
        e.getPresentation().setEnabled(enabled);
        e.getPresentation().setDescription(enabled
                ? UiText.text(language, "Preview generated changes against existing files")
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
        UmabootLog log = UmabootLog.get(project);
        log.started("preview merge", "config: " + configPath);
        new Task.Backgroundable(project, UiText.text(language, "Umaboot: Previewing generated changes"), true) {
            @Override
            public void run(ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                indicator.setText(UiText.text(language, "Preparing generated output..."));

                try {
                    UmabootRunner.Plan plan = new UmabootRunner().prepare(configPath);
                    log.detail("preview merge", "outputDir: " + plan.outputDir());
                    log.detail("preview merge", "architecture: " + plan.architecture());
                    log.detail("preview merge", "persistence: " + plan.persistence());
                    log.detail("preview merge", "mode: " + plan.mode() + (plan.autoOverlay() ? " (auto)" : ""));
                    log.detail("preview merge", "generated units: " + plan.units().size());
                    log.parserWarnings("preview merge", plan.warnings());
                    Preview preview = buildPreview(plan);
                    log.finished("preview merge",
                            preview.modified() + " modified, " + preview.added()
                                    + " new, " + preview.unchanged() + " unchanged files");
                    for (Change change : preview.changes()) {
                        log.detail("preview merge", (change.added() ? "new: " : "modified: ")
                                + change.relativePath());
                    }
                    ApplicationManager.getApplication().invokeLater(() ->
                            openPreview(project, language, plan, preview, log));
                } catch (Exception ex) {
                    log.failed("preview merge", ex);
                    log.showDetail();
                    notifyUser(project,
                            UiText.format(language, "Umaboot preview failed: %s. See detail log for diagnostics.",
                                    UmabootLog.rootMessage(ex)),
                            NotificationType.ERROR);
                }
            }
        }.queue();
    }

    private static Preview buildPreview(UmabootRunner.Plan plan) throws IOException {
        List<Change> changes = new ArrayList<>();
        int unchanged = 0;
        int added = 0;
        int modified = 0;
        List<GeneratedUnit> units = plan.ctx().overlay()
                ? overlayPreviewUnits(plan)
                : plan.units();

        for (GeneratedUnit unit : units) {
            Path target = targetPath(plan.outputDir(), unit);
            String generated = unit.content();
            if (!Files.exists(target)) {
                changes.add(new Change(unit.relativePath(), target, "", generated, true));
                added++;
                continue;
            }

            String current = Files.readString(target, StandardCharsets.UTF_8);
            if (current.equals(generated)) {
                unchanged++;
            } else {
                changes.add(new Change(unit.relativePath(), target, current, generated, false));
                modified++;
            }
        }
        return new Preview(changes, added, modified, unchanged);
    }

    private static List<GeneratedUnit> overlayPreviewUnits(UmabootRunner.Plan plan) {
        OverlayPlan overlayPlan = new OverlayPlanner().plan(plan.units(), plan.outputDir(), plan.ctx());
        return overlayPlan.previewUnits();
    }

    private static Path targetPath(Path outputDir, GeneratedUnit unit) {
        return outputDir.resolve(unit.relativePath().replace('/', java.io.File.separatorChar));
    }

    private static void openPreview(Project project, UiText.Language language,
                                    UmabootRunner.Plan plan, Preview preview, UmabootLog log) {
        if (preview.changes().isEmpty()) {
            log.event("preview merge", "no generated file changes found");
            notifyUser(project, UiText.text(language, "No generated file changes found."),
                    NotificationType.INFORMATION);
            return;
        }

        String message = UiText.format(language,
                "Umaboot: %d modified, %d new, %d unchanged files. Open merge windows?",
                preview.modified(), preview.added(), preview.unchanged());
        int choice = Messages.showYesNoDialog(
                project,
                message,
                UiText.text(language, "Umaboot: Preview / Merge"),
                UiText.text(language, "Open Merge"),
                UiText.text(language, "Cancel"),
                Messages.getQuestionIcon());
        if (choice != Messages.YES) {
            log.event("preview merge", "merge cancelled by user");
            return;
        }

        log.event("preview merge", "opening " + preview.changes().size() + " merge windows");
        notifyUser(project,
                UiText.format(language, "Opening %d merge windows.", preview.changes().size()),
                NotificationType.INFORMATION);

        int token = PreviewMergeState.get(project).start(preview.changes().size());
        log.event("preview merge", "started session " + token);
        for (Change change : preview.changes()) {
            openMerge(project, language, plan, change, log, token);
        }
    }

    private static void openMerge(Project project, UiText.Language language,
                                  UmabootRunner.Plan plan, Change change, UmabootLog log, int token) {
        log.detail("preview merge", "opening merge: " + change.relativePath());
        Document output = EditorFactory.getInstance().createDocument(change.current());
        List<String> contents = List.of(change.current(), change.current(), change.generated());
        List<String> titles = change.added()
                ? List.of(
                        UiText.text(language, "Missing file"),
                        UiText.text(language, "Base"),
                        UiText.text(language, "Generated file"))
                : List.of(
                        UiText.text(language, "Current file"),
                        UiText.text(language, "Base"),
                        UiText.text(language, "Generated file"));

        try {
            MergeRequest request = DiffRequestFactory.getInstance().createMergeRequest(
                    project,
                    FileTypes.PLAIN_TEXT,
                    output,
                    contents,
                    UiText.format(language, "Merge generated file: %s", change.relativePath()),
                    titles,
                    result -> {
                        if (result == MergeResult.CANCEL) {
                            log.detail("preview merge", "merge cancelled: " + change.relativePath());
                            PreviewMergeState.get(project).complete(token);
                            return;
                        }
                        if (!PreviewMergeState.get(project).isActive(token)) {
                            log.detail("preview merge", "merge ignored after reset: " + change.relativePath());
                            notifyUser(project,
                                    UiText.format(language, "Preview / Merge choices were reset; skipped %s.",
                                            change.relativePath()),
                                    NotificationType.WARNING);
                            return;
                        }
                        try {
                            writeAcceptedFile(project, language, plan.outputDir(), change, output.getText(), log);
                        } finally {
                            PreviewMergeState.get(project).complete(token);
                        }
                    });
            DiffManager.getInstance().showMerge(project, request);
        } catch (InvalidDiffRequestException ex) {
            log.failed("preview merge", ex);
            notifyUser(project,
                    UiText.format(language, "Failed to open merge for %s: %s",
                            change.relativePath(), ex.getMessage()),
                    NotificationType.ERROR);
        }
    }

    private static void writeAcceptedFile(Project project, UiText.Language language,
                                          Path outputDir, Change change, String content, UmabootLog log) {
        try {
            Files.createDirectories(change.target().getParent());
            Files.writeString(change.target(), content, StandardCharsets.UTF_8);
            refresh(outputDir, change.target());
            log.event("preview merge", "merged generated file: " + change.relativePath());
            notifyUser(project,
                    UiText.format(language, "Merged generated file: %s", change.relativePath()),
                    NotificationType.INFORMATION);
        } catch (IOException ex) {
            log.failed("preview merge", ex);
            notifyUser(project,
                    UiText.format(language, "Failed to write %s: %s",
                            change.relativePath(), ex.getMessage()),
                    NotificationType.ERROR);
        }
    }

    private static void refresh(Path outputDir, Path target) {
        VirtualFile output = LocalFileSystem.getInstance()
                .refreshAndFindFileByPath(outputDir.toString());
        if (output != null) output.refresh(true, true);
        VirtualFile file = LocalFileSystem.getInstance()
                .refreshAndFindFileByPath(target.toString());
        if (file != null) file.refresh(false, false);
    }

    private static void notifyUser(Project project, String message, NotificationType type) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("Umaboot")
                .createNotification(message, type)
                .notify(project);
    }

    private record Preview(List<Change> changes, int added, int modified, int unchanged) {}

    private record Change(String relativePath, Path target, String current, String generated, boolean added) {}
}

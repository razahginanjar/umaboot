package io.umaboot.intellij;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import io.umaboot.intellij.settings.UiText;
import org.jetbrains.annotations.Nullable;

/**
 * Adds a green play-button icon in the gutter of {@code umaboot.yaml}.
 * Clicking the icon invokes the {@code Umaboot.Generate} action without
 * leaving the file.
 */
public final class UmabootRunLineMarkerProvider implements LineMarkerProvider {

    @Override
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(PsiElement element) {
        // Only mark the very first non-whitespace element of umaboot.yaml.
        PsiFile file = element.getContainingFile();
        if (file == null) return null;
        if (!UmabootConfigLocator.isConfigFileName(file.getName())) return null;
        if (element.getTextOffset() != firstNonWhitespaceOffset(file)) return null;
        String tooltip = UiText.text(UiText.load(file.getProject()), "Run Umaboot: Generate");

        return new LineMarkerInfo<>(
                element,
                element.getTextRange(),
                com.intellij.icons.AllIcons.RunConfigurations.TestState.Run,
                psi -> tooltip,
                (e, psi) -> runGenerate(psi.getProject()),
                GutterIconRenderer.Alignment.LEFT,
                () -> tooltip
        );
    }

    private static int firstNonWhitespaceOffset(PsiFile file) {
        String text = file.getText();
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) return i;
        }
        return 0;
    }

    private static void runGenerate(Project project) {
        var action = ActionManager.getInstance().getAction("Umaboot.Generate");
        if (action == null) return;
        DataContext ctx = SimpleDataContext.getProjectContext(project);
        AnActionEvent event = AnActionEvent.createFromDataContext("UmabootGutter", new Presentation(), ctx);
        action.actionPerformed(event);
    }
}

package io.umaboot.intellij;

import com.intellij.ide.IconProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/**
 * Substitutes the Umaboot file icon for files named {@code umaboot.yaml}
 * in the Project view, file-finder dialogs, and editor tabs. The file remains
 * a regular YAML file for editing, parsing, and validation; only the icon
 * changes.
 */
public final class UmabootFileIconProvider extends IconProvider {

    @Override
    public @Nullable Icon getIcon(PsiElement element, int flags) {
        if (!(element instanceof PsiFile psiFile)) return null;
        VirtualFile vf = psiFile.getVirtualFile();
        if (vf == null) return null;
        if (UmabootConfigLocator.isConfigFileName(vf.getName())) {
            return UmabootIcons.FILE;
        }
        return null;
    }
}

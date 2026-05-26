import * as vscode from 'vscode';
import { isUmabootConfigPath } from './paths';

/**
 * Adds a small {@code U} badge (and a theme-aware accent color) to
 * {@code umaboot.yaml} files in the Explorer / open-editors view.
 *
 * <p>VS Code doesn't let extensions register custom file icons (those come
 * from icon themes), but {@link vscode.FileDecorationProvider} lets us
 * augment the existing icon with a one- or two-character badge plus a
 * {@link vscode.ThemeColor color}. It's the closest thing to the IntelliJ
 * plugin's custom file-icon provider.</p>
 */
export class UmabootFileDecorationProvider implements vscode.FileDecorationProvider {

    private readonly _onDidChange = new vscode.EventEmitter<vscode.Uri[]>();
    readonly onDidChangeFileDecorations = this._onDidChange.event;

    refresh(uris: vscode.Uri[]): void {
        this._onDidChange.fire(uris);
    }

    provideFileDecoration(uri: vscode.Uri): vscode.FileDecoration | undefined {
        if (!isUmabootConfigPath(uri.fsPath)) return undefined;
        return {
            badge: 'U',
            color: new vscode.ThemeColor('charts.orange'),
            tooltip: 'Umaboot configuration',
            propagate: false,
        };
    }
}

import * as vscode from 'vscode';
import { isUmabootConfigPath } from './paths';

/**
 * CodeLens provider that surfaces ▶ Generate / ⇄ Diff / ✓ Apply links above
 * the first line of `umaboot.yaml`. The same affordance the IntelliJ plugin
 * provides via the gutter run icon.
 *
 * <p>Activates on any YAML file whose basename matches {@code umaboot.yaml} or
 * the legacy {@code crudforge.yaml}. Anchored at line 0 so it's always
 * visible without scrolling.</p>
 */
export class UmabootCodeLensProvider implements vscode.CodeLensProvider {

    private readonly _onDidChange = new vscode.EventEmitter<void>();
    readonly onDidChangeCodeLenses = this._onDidChange.event;

    refresh(): void {
        this._onDidChange.fire();
    }

    provideCodeLenses(document: vscode.TextDocument): vscode.CodeLens[] {
        if (!isUmabootConfigPath(document.uri.fsPath)) return [];
        const range = new vscode.Range(0, 0, 0, 0);
        return [
            new vscode.CodeLens(range, {
                title: '$(play) Generate',
                tooltip: 'Run Umaboot generate against this config',
                command: 'umaboot.generate',
            }),
            new vscode.CodeLens(range, {
                title: '$(diff) Diff',
                tooltip: 'Show pending changes without writing',
                command: 'umaboot.diff',
            }),
            new vscode.CodeLens(range, {
                title: '$(diff) Preview / Merge',
                tooltip: 'Preview generated files and accept selected changes',
                command: 'umaboot.previewMerge',
            }),
            new vscode.CodeLens(range, {
                title: '$(check) Apply',
                tooltip: 'Apply changes preserving protected regions',
                command: 'umaboot.apply',
            }),
            new vscode.CodeLens(range, {
                title: '$(plug) Test Connection',
                tooltip: 'Verify the JDBC URL is reachable',
                command: 'umaboot.testConnection',
            }),
        ];
    }
}

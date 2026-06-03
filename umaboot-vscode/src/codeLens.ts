import * as vscode from 'vscode';
import { text, UiLanguage } from './i18n';
import { isUmabootConfigPath } from './paths';

/**
 * CodeLens provider that surfaces Generate / Diff / Preview / Apply Generated
 * Files links above the first line of `umaboot.yaml`. The same affordance the
 * IntelliJ plugin provides via the gutter run icon.
 *
 * <p>Activates on any YAML file whose basename matches {@code umaboot.yaml} or
 * the legacy {@code crudforge.yaml}. Anchored at line 0 so it's always
 * visible without scrolling.</p>
 */
export class UmabootCodeLensProvider implements vscode.CodeLensProvider {

    private readonly _onDidChange = new vscode.EventEmitter<void>();
    readonly onDidChangeCodeLenses = this._onDidChange.event;

    constructor(private readonly language: () => UiLanguage) {}

    refresh(): void {
        this._onDidChange.fire();
    }

    provideCodeLenses(document: vscode.TextDocument): vscode.CodeLens[] {
        if (!isUmabootConfigPath(document.uri.fsPath)) return [];
        const range = new vscode.Range(0, 0, 0, 0);
        const language = this.language();
        return [
            new vscode.CodeLens(range, {
                title: `$(play) ${text(language, 'Generate')}`,
                tooltip: text(language, 'Run Umaboot generate against this config'),
                command: 'umaboot.generate',
            }),
            new vscode.CodeLens(range, {
                title: `$(diff) ${text(language, 'Diff')}`,
                tooltip: text(language, 'Show pending changes without writing'),
                command: 'umaboot.diff',
            }),
            new vscode.CodeLens(range, {
                title: `$(diff) ${text(language, 'Preview / Merge')}`,
                tooltip: text(language, 'Preview generated files and accept selected changes'),
                command: 'umaboot.previewMerge',
            }),
            new vscode.CodeLens(range, {
                title: `$(check) ${text(language, 'Apply Generated Files')}`,
                tooltip: text(language, 'Apply generated files preserving protected regions'),
                command: 'umaboot.apply',
            }),
            new vscode.CodeLens(range, {
                title: `$(plug) ${text(language, 'Test Connection')}`,
                tooltip: text(language, 'Verify the JDBC URL is reachable'),
                command: 'umaboot.testConnection',
            }),
        ];
    }
}

import * as cp from 'child_process';
import * as crypto from 'crypto';
import * as fs from 'fs';
import * as path from 'path';
import * as vscode from 'vscode';
import * as yaml from 'js-yaml';

/**
 * Manages the "Edit Configuration" webview panel.
 *
 * Pattern: a single panel per workspace, reused across "Open" calls. The host
 * owns YAML I/O (read on open, write on Save). The webview is a dumb form —
 * see {@code media/configEditor.js} for the bidirectional message protocol.
 *
 * <p>Limitations matching the existing IntelliJ plugin's {@code UmabootYamlIO}:
 * comments in the YAML are NOT preserved on round-trip. Users who keep
 * commented configs should hand-edit instead.</p>
 */
export class UmabootConfigEditor {

    private static current: UmabootConfigEditor | undefined;

    static open(context: vscode.ExtensionContext, channel: vscode.OutputChannel): void {
        if (UmabootConfigEditor.current) {
            UmabootConfigEditor.current.panel.reveal();
            return;
        }
        const folder = vscode.workspace.workspaceFolders?.[0];
        if (!folder) {
            vscode.window.showErrorMessage('Umaboot: open a folder first.');
            return;
        }
        const panel = vscode.window.createWebviewPanel(
            'umaboot.configEditor',
            'Umaboot — Configuration',
            vscode.ViewColumn.Active,
            {
                enableScripts: true,
                retainContextWhenHidden: true,
                localResourceRoots: [
                    vscode.Uri.joinPath(context.extensionUri, 'media'),
                ],
            },
        );
        UmabootConfigEditor.current = new UmabootConfigEditor(context, panel, channel, folder.uri.fsPath);
    }

    private constructor(
        private readonly context: vscode.ExtensionContext,
        readonly panel: vscode.WebviewPanel,
        private readonly channel: vscode.OutputChannel,
        private readonly workspaceRoot: string,
    ) {
        panel.iconPath = vscode.Uri.joinPath(context.extensionUri, 'resources', 'icon.png');
        panel.webview.html = this.buildHtml();
        panel.webview.onDidReceiveMessage((msg) => this.handleMessage(msg));
        panel.onDidDispose(() => {
            UmabootConfigEditor.current = undefined;
        });
    }

    // ============================================================
    // HTML rendering
    // ============================================================

    private buildHtml(): string {
        const mediaUri = vscode.Uri.joinPath(this.context.extensionUri, 'media');
        const styleUri = this.panel.webview.asWebviewUri(vscode.Uri.joinPath(mediaUri, 'configEditor.css'));
        const scriptUri = this.panel.webview.asWebviewUri(vscode.Uri.joinPath(mediaUri, 'configEditor.js'));
        const nonce = crypto.randomBytes(16).toString('hex');
        const cspSource = this.panel.webview.cspSource;

        const htmlPath = path.join(this.context.extensionPath, 'media', 'configEditor.html');
        const template = fs.readFileSync(htmlPath, 'utf8');
        return template
            .replace(/\{\{cspSource\}\}/g, cspSource)
            .replace(/\{\{nonce\}\}/g, nonce)
            .replace(/\{\{styleUri\}\}/g, styleUri.toString())
            .replace(/\{\{scriptUri\}\}/g, scriptUri.toString());
    }

    // ============================================================
    // Messaging
    // ============================================================

    private handleMessage(msg: { command?: string; config?: unknown }): void {
        switch (msg.command) {
            case 'ready':
                this.sendCurrentConfig();
                break;
            case 'save':
                this.saveConfig(msg.config);
                break;
            case 'testConnection':
                this.runCli('test-connection', msg.config, (r) => ({
                    command: 'connectionResult',
                    ok: r.code === 0,
                    message: r.code === 0
                        ? r.stdout.trim().split(/\r?\n/)[0]
                        : (r.stderr || r.stdout).trim().split(/\r?\n/)[0] || `failed (exit ${r.code})`,
                }));
                break;
            case 'refreshTables':
                this.runCli('list-tables', msg.config, (r) => {
                    if (r.code !== 0) {
                        return {
                            command: 'connectionResult',
                            ok: false,
                            message: (r.stderr || r.stdout).trim().split(/\r?\n/)[0] || `failed (exit ${r.code})`,
                        };
                    }
                    const tables = r.stdout.trim().split(/\r?\n/).filter((s) => s.length > 0);
                    return { command: 'tablesResult', tables };
                });
                break;
            case 'browseSchemaFile':
                this.browseSchemaFile();
                break;
            default:
                break;
        }
    }

    // ============================================================
    // YAML I/O
    // ============================================================

    private configFilePath(): string {
        const rel = vscode.workspace.getConfiguration('umaboot').get<string>('configFile') ?? 'umaboot.yaml';
        const abs = path.isAbsolute(rel) ? rel : path.join(this.workspaceRoot, rel);
        if (fs.existsSync(abs)) return abs;
        const legacy = path.join(this.workspaceRoot, 'crudforge.yaml');
        if (fs.existsSync(legacy)) return legacy;
        return abs;
    }

    private sendCurrentConfig(): void {
        const filePath = this.configFilePath();
        let cfg: Record<string, unknown> = {};
        try {
            if (fs.existsSync(filePath)) {
                const raw = fs.readFileSync(filePath, 'utf8');
                cfg = (yaml.load(raw) as Record<string, unknown>) ?? {};
            }
        } catch (err) {
            vscode.window.showWarningMessage(
                `Umaboot: failed to parse ${path.basename(filePath)} — opening with empty defaults.`,
            );
            this.channel.appendLine(`[config-editor] parse error: ${err}`);
        }
        this.panel.webview.postMessage({ command: 'load', config: cfg });
    }

    private saveConfig(config: unknown): void {
        if (!config || typeof config !== 'object') return;
        const filePath = this.configFilePath();
        try {
            const yamlStr = yaml.dump(config, {
                indent: 2,
                lineWidth: 120,
                noRefs: true,
                quotingType: '"',
                forceQuotes: false,
            });
            const banner = '# Generated by Umaboot configuration editor.\n'
                + '# Comments are not preserved on round-trip; hand-edit if you want them.\n';
            fs.writeFileSync(filePath, banner + yamlStr, 'utf8');
            this.channel.appendLine(`[config-editor] saved ${filePath}`);
            vscode.window.showInformationMessage(`Umaboot: saved ${path.basename(filePath)}.`);
            this.panel.webview.postMessage({ command: 'saved' });
        } catch (err) {
            vscode.window.showErrorMessage(`Umaboot: save failed — ${err}`);
            this.channel.appendLine(`[config-editor] save error: ${err}`);
        }
    }

    private async browseSchemaFile(): Promise<void> {
        const picked = await vscode.window.showOpenDialog({
            title: 'Select Schema SQL File',
            defaultUri: vscode.Uri.file(this.workspaceRoot),
            canSelectFiles: true,
            canSelectFolders: false,
            canSelectMany: false,
            filters: {
                'SQL files': ['sql'],
                'All files': ['*'],
            },
        });
        const file = picked?.[0];
        if (!file) return;
        let value = file.fsPath;
        const rel = path.relative(this.workspaceRoot, file.fsPath);
        if (rel && !rel.startsWith('..') && !path.isAbsolute(rel)) {
            value = rel.split(path.sep).join('/');
        }
        this.panel.webview.postMessage({ command: 'schemaFileSelected', path: value });
    }

    // ============================================================
    // CLI shell-out for live introspection from the form
    // ============================================================

    /**
     * Persist the form's current config to a temp file, run the CLI subcommand
     * against that file, and feed the result back through {@code mapResult}.
     * We don't reuse the workspace umaboot.yaml because the user may have
     * unsaved tweaks they want to validate before committing.
     */
    private runCli(
        subcommand: 'test-connection' | 'list-tables',
        config: unknown,
        mapResult: (r: { stdout: string; stderr: string; code: number | null }) => Record<string, unknown>,
    ): void {
        const cli = vscode.workspace.getConfiguration('umaboot').get<string>('cliPath') ?? 'umaboot';
        const tmpFile = path.join(
            this.context.globalStorageUri.fsPath,
            `editor-${Date.now()}.yaml`,
        );
        try {
            fs.mkdirSync(path.dirname(tmpFile), { recursive: true });
            fs.writeFileSync(
                tmpFile,
                yaml.dump(config, { indent: 2, noRefs: true }),
                'utf8',
            );
        } catch (err) {
            this.panel.webview.postMessage({
                command: 'connectionResult',
                ok: false,
                message: `failed to write temp config: ${err}`,
            });
            return;
        }

        this.channel.appendLine(`> ${cli} ${subcommand} --config ${tmpFile}`);
        const child = cp.spawn(cli, [subcommand, '--config', tmpFile], {
            cwd: this.workspaceRoot,
            shell: true,
        });
        let stdout = '';
        let stderr = '';
        child.stdout.on('data', (d: Buffer) => { stdout += d.toString(); });
        child.stderr.on('data', (d: Buffer) => { stderr += d.toString(); });
        child.on('close', (code) => {
            try { fs.unlinkSync(tmpFile); } catch { /* ignore */ }
            const reply = mapResult({ stdout, stderr, code });
            this.panel.webview.postMessage(reply);
        });
    }
}

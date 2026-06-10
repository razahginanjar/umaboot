import * as cp from 'child_process';
import * as crypto from 'crypto';
import * as fs from 'fs';
import * as https from 'https';
import * as path from 'path';
import * as vscode from 'vscode';
import * as yaml from 'js-yaml';
import { CliResolutionError, ResolvedCliCommand, resolveCliCommand } from './cli';
import { format, getLanguage, isLanguage, setLanguage, text } from './i18n';
import { UmabootLogger } from './logging';
import { isSchemaFileConfig, tableNamesFromSchemaFileConfig } from './sqlTables';

const LOMBOK_MAVEN_CENTRAL_URL = 'https://search.maven.org/solrsearch/select'
    + '?q=g:org.projectlombok+AND+a:lombok&core=gav&rows=40&sort=timestamp+desc&wt=json';

const LOMBOK_VERSION_FALLBACK = [
    '1.18.46',
    '1.18.44',
    '1.18.42',
    '1.18.40',
    '1.18.38',
    '1.18.36',
    '1.18.34',
    '1.18.32',
    '1.18.30',
];

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

    static open(
        context: vscode.ExtensionContext,
        logger: UmabootLogger,
        onLanguageChanged?: () => void,
    ): void {
        if (UmabootConfigEditor.current) {
            UmabootConfigEditor.current.panel.reveal();
            return;
        }
        const folder = vscode.workspace.workspaceFolders?.[0];
        if (!folder) {
            vscode.window.showErrorMessage(
                text(getLanguage(context), 'Umaboot: open a folder first.'),
            );
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
        UmabootConfigEditor.current = new UmabootConfigEditor(
            context,
            panel,
            logger,
            folder.uri.fsPath,
            onLanguageChanged,
        );
    }

    private constructor(
        private readonly context: vscode.ExtensionContext,
        readonly panel: vscode.WebviewPanel,
        private readonly logger: UmabootLogger,
        private readonly workspaceRoot: string,
        private readonly onLanguageChanged: (() => void) | undefined,
    ) {
        panel.iconPath = vscode.Uri.joinPath(context.extensionUri, 'resources', 'toolwindow.png');
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

    private handleMessage(msg: { command?: string; config?: unknown; language?: unknown }): void {
        switch (msg.command) {
            case 'ready':
                this.sendCurrentConfig();
                this.sendLombokVersions();
                break;
            case 'syncFromYaml':
                this.sendCurrentConfig({ synced: true, preserveOnError: true });
                break;
            case 'setLanguage':
                this.updateLanguage(msg.language);
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
            case 'refreshTables': {
                const scriptMode = isSchemaFileConfig(msg.config);
                if (scriptMode) {
                    const tables = tableNamesFromSchemaFileConfig(msg.config, this.workspaceRoot);
                    if (tables.length > 0) {
                        this.logger.event('config editor', `parsed ${tables.length} CREATE TABLE names from schema file`);
                        this.panel.webview.postMessage({ command: 'tablesResult', tables, scriptMode });
                        break;
                    }
                }
                this.runCli('list-tables', msg.config, (r) => {
                    if (r.code !== 0) {
                        return {
                            command: 'connectionResult',
                            ok: false,
                            message: (r.stderr || r.stdout).trim().split(/\r?\n/)[0] || `failed (exit ${r.code})`,
                        };
                    }
                    const tables = r.stdout.trim().split(/\r?\n/).filter((s) => s.length > 0);
                    const warning = (r.stderr || '').trim().split(/\r?\n/).filter((s) => s.length > 0)[0];
                    return { command: 'tablesResult', tables, scriptMode, message: warning };
                }, scriptMode ? ['--raw', '--all'] : []);
                break;
            }
            case 'describeSchema':
                this.runCli('describe-schema', msg.config, (r) => {
                    if (r.code !== 0) {
                        return {
                            command: 'schemaMetadataResult',
                            ok: false,
                            message: (r.stderr || r.stdout).trim().split(/\r?\n/)[0] || `failed (exit ${r.code})`,
                        };
                    }
                    try {
                        const metadata = JSON.parse(r.stdout);
                        const warnings = Array.isArray(metadata?.warnings) ? metadata.warnings : [];
                        if (warnings.length > 0) {
                            this.logger.event('config editor describe-schema',
                                `schema parsed with ${warnings.length} warning(s)`);
                        }
                        return {
                            command: 'schemaMetadataResult',
                            ok: true,
                            metadata,
                        };
                    } catch (err) {
                        return {
                            command: 'schemaMetadataResult',
                            ok: false,
                            message: `invalid schema metadata JSON: ${err}`,
                        };
                    }
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

    private sendCurrentConfig(options: { synced?: boolean; preserveOnError?: boolean } = {}): void {
        const filePath = this.configFilePath();
        let cfg: Record<string, unknown> = {};
        try {
            if (fs.existsSync(filePath)) {
                const raw = fs.readFileSync(filePath, 'utf8');
                cfg = (yaml.load(raw) as Record<string, unknown>) ?? {};
            }
        } catch (err) {
            this.logger.error('config editor', `parse error: ${err}`);
            if (options.preserveOnError) {
                const message = format(
                    getLanguage(this.context),
                    'Umaboot: failed to parse {0}; current form was not changed.',
                    path.basename(filePath),
                );
                vscode.window.showWarningMessage(message);
                this.panel.webview.postMessage({
                    command: 'syncFailed',
                    message,
                });
                return;
            }
            vscode.window.showWarningMessage(
                format(
                    getLanguage(this.context),
                    'Umaboot: failed to parse {0}; opening with empty defaults.',
                    path.basename(filePath),
                ),
            );
        }
        this.panel.webview.postMessage({
            command: 'load',
            config: cfg,
            language: getLanguage(this.context),
            synced: options.synced === true,
        });
    }

    private sendLombokVersions(): void {
        void fetchLombokVersions()
            .catch((err) => {
                this.logger.event('config editor', `Lombok version fetch failed: ${err}`);
                return LOMBOK_VERSION_FALLBACK;
            })
            .then((versions) => {
                this.panel.webview.postMessage({
                    command: 'lombokVersionsResult',
                    versions,
                });
            });
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
            this.logger.event('config editor', `saved ${filePath}`);
            vscode.window.showInformationMessage(
                format(getLanguage(this.context), 'Umaboot: saved {0}.', path.basename(filePath)),
            );
            this.panel.webview.postMessage({ command: 'saved' });
        } catch (err) {
            vscode.window.showErrorMessage(
                format(getLanguage(this.context), 'Umaboot: save failed; {0}', String(err)),
            );
            this.logger.error('config editor', `save failed: ${err}`);
        }
    }

    private async browseSchemaFile(): Promise<void> {
        const picked = await vscode.window.showOpenDialog({
            title: text(getLanguage(this.context), 'Select Schema SQL File'),
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

    private updateLanguage(language: unknown): void {
        if (!isLanguage(language)) return;
        void setLanguage(this.context, language).then(() => {
            this.onLanguageChanged?.();
        });
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
        subcommand: 'test-connection' | 'list-tables' | 'describe-schema',
        config: unknown,
        mapResult: (r: { stdout: string; stderr: string; code: number | null }) => Record<string, unknown>,
        extraArgs: string[] = [],
    ): void {
        const cliPath = vscode.workspace.getConfiguration('umaboot').get<string>('cliPath') ?? 'umaboot';
        const tmpFile = path.join(
            this.context.globalStorageUri.fsPath,
            `editor-${Date.now()}.yaml`,
        );
        try {
            fs.mkdirSync(path.dirname(tmpFile), { recursive: true });
            fs.writeFileSync(
                tmpFile,
                yaml.dump(this.configForTempYaml(config), { indent: 2, noRefs: true }),
                'utf8',
            );
        } catch (err) {
            this.panel.webview.postMessage({
                command: 'connectionResult',
                ok: false,
                message: format(getLanguage(this.context), 'failed to write temp config: {0}', String(err)),
            });
            return;
        }

        const args = [subcommand, '--config', tmpFile, ...extraArgs];
        let cli: ResolvedCliCommand;
        try {
            cli = resolveCliCommand(cliPath, args, this.workspaceRoot);
        } catch (err) {
            try { fs.unlinkSync(tmpFile); } catch { /* ignore */ }
            this.panel.webview.postMessage({
                command: 'connectionResult',
                ok: false,
                message: cliResolutionMessage(err),
            });
            return;
        }
        this.logger.command(`config editor ${subcommand}`, cli.command, cli.args, this.workspaceRoot);
        const child = cp.spawn(cli.command, cli.args, {
            cwd: this.workspaceRoot,
            shell: cli.shell,
        });
        let stdout = '';
        let stderr = '';
        child.stdout.on('data', (d: Buffer) => { stdout += d.toString(); });
        child.stderr.on('data', (d: Buffer) => { stderr += d.toString(); });
        child.on('close', (code) => {
            try { fs.unlinkSync(tmpFile); } catch { /* ignore */ }
            this.logger.result(`config editor ${subcommand}`, code, stdout, stderr, {
                includeStdout: subcommand !== 'describe-schema',
            });
            const reply = mapResult({ stdout, stderr, code });
            this.panel.webview.postMessage(reply);
        });
    }

    private configForTempYaml(config: unknown): unknown {
        if (!config || typeof config !== 'object') return config;
        const copy = JSON.parse(JSON.stringify(config)) as unknown;
        if (!isRecord(copy)) return copy;

        normalizeSchemaFile(copy, this.workspaceRoot);
        const generation = copy.generation;
        if (isRecord(generation)) {
            normalizeSchemaFile(generation, this.workspaceRoot);
        }
        return copy;
    }
}

function isRecord(value: unknown): value is Record<string, unknown> {
    return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}

function fetchLombokVersions(): Promise<string[]> {
    return new Promise((resolve, reject) => {
        const req = https.get(
            LOMBOK_MAVEN_CENTRAL_URL,
            {
                headers: {
                    Accept: 'application/json',
                    'User-Agent': 'umaboot-vscode',
                },
            },
            (res) => {
                if (res.statusCode !== 200) {
                    res.resume();
                    reject(new Error(`Maven Central returned HTTP ${res.statusCode}`));
                    return;
                }
                let body = '';
                res.setEncoding('utf8');
                res.on('data', (chunk: string) => {
                    body += chunk;
                });
                res.on('end', () => {
                    try {
                        const versions = parseLombokVersions(body);
                        resolve(versions.length > 0 ? versions : LOMBOK_VERSION_FALLBACK);
                    } catch (err) {
                        reject(err);
                    }
                });
            },
        );
        req.setTimeout(6000, () => {
            req.destroy(new Error('Maven Central request timed out'));
        });
        req.on('error', reject);
    });
}

function parseLombokVersions(body: string): string[] {
    const parsed = JSON.parse(body) as unknown;
    if (!isRecord(parsed) || !isRecord(parsed.response) || !Array.isArray(parsed.response.docs)) {
        return [];
    }

    const seen = new Set<string>();
    for (const doc of parsed.response.docs) {
        if (!isRecord(doc) || typeof doc.v !== 'string') continue;
        if (isSupportedLombokVersion(doc.v)) {
            seen.add(doc.v);
        }
    }
    return Array.from(seen).sort(compareVersionsDesc);
}

function isSupportedLombokVersion(version: string): boolean {
    const match = /^1\.18\.(\d+)$/.exec(version);
    return Boolean(match && Number.parseInt(match[1], 10) >= 30);
}

function compareVersionsDesc(left: string, right: string): number {
    const leftParts = left.split('.').map((part) => Number.parseInt(part, 10) || 0);
    const rightParts = right.split('.').map((part) => Number.parseInt(part, 10) || 0);
    for (let i = 0; i < Math.max(leftParts.length, rightParts.length); i += 1) {
        const diff = (rightParts[i] || 0) - (leftParts[i] || 0);
        if (diff !== 0) return diff;
    }
    return 0;
}

function stringValue(value: unknown): string {
    return typeof value === 'string' ? value.trim() : '';
}

function normalizeSchemaFile(config: Record<string, unknown>, workspaceRoot: string): void {
    const schemaFile = stringValue(config.schemaFile);
    if (!schemaFile || path.isAbsolute(schemaFile)) return;
    config.schemaFile = path.resolve(workspaceRoot, schemaFile);
}

function cliResolutionMessage(err: unknown): string {
    if (err instanceof CliResolutionError) {
        return err.message;
    }
    return String(err);
}

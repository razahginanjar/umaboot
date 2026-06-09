import * as cp from 'child_process';
import * as fs from 'fs';
import * as path from 'path';
import * as vscode from 'vscode';
import * as yaml from 'js-yaml';
import { UmabootTreeProvider } from './tree';
import { UmabootCodeLensProvider } from './codeLens';
import { UmabootFileDecorationProvider } from './fileDecoration';
import { UmabootConfigEditor } from './configEditor';
import { CliResolutionError, ResolvedCliCommand, resolveCliCommand } from './cli';
import { format, getLanguage, text, UiLanguage } from './i18n';
import { UmabootLogger } from './logging';
import { isUmabootConfigPath } from './paths';
import { isSchemaFileConfig, tableNamesFromSchemaFileConfig } from './sqlTables';

/**
 * Umaboot VS Code extension entry point.
 *
 * Phases delivered here:
 * - R.1: Activity Bar dashboard tree view (Configuration / Tables / Actions),
 *        watcher on umaboot.yaml, command palette entries.
 * - R.2: CodeLens on umaboot.yaml, FileDecorationProvider badge, status bar
 *        item, and introspection commands (Test Connection, Refresh Tables)
 *        backed by the new umaboot CLI subcommands.
 */
export function activate(context: vscode.ExtensionContext): void {
    const logger = new UmabootLogger();

    const folder = vscode.workspace.workspaceFolders?.[0];
    const workspaceRoot = folder?.uri.fsPath;

    const configRelPath = (): string =>
        vscode.workspace.getConfiguration('umaboot').get<string>('configFile') ?? 'umaboot.yaml';
    const language = (): UiLanguage => getLanguage(context);
    const msg = (key: string, ...args: Array<string | number | null | undefined>): string =>
        format(language(), key, ...args);

    // --- Tree view ---------------------------------------------------------
    const treeProvider = new UmabootTreeProvider(workspaceRoot, configRelPath, language);
    context.subscriptions.push(
        vscode.window.registerTreeDataProvider('umaboot.dashboard', treeProvider),
    );

    // --- CodeLens on umaboot.yaml ------------------------------------------
    const codeLensProvider = new UmabootCodeLensProvider(language);
    context.subscriptions.push(
        vscode.languages.registerCodeLensProvider(
            { language: 'yaml', scheme: 'file' },
            codeLensProvider,
        ),
    );

    // --- File decoration badge (Explorer, open editors) --------------------
    const fileDecorations = new UmabootFileDecorationProvider();
    context.subscriptions.push(
        vscode.window.registerFileDecorationProvider(fileDecorations),
    );

    // --- Status bar item ---------------------------------------------------
    const statusBar = vscode.window.createStatusBarItem(
        vscode.StatusBarAlignment.Left, 100,
    );
    statusBar.command = 'workbench.view.extension.umaboot';
    context.subscriptions.push(statusBar);
    refreshStatusBar(statusBar, workspaceRoot, configRelPath, language());

    const refreshLanguageSensitiveUi = (): void => {
        treeProvider.refresh();
        codeLensProvider.refresh();
        refreshStatusBar(statusBar, workspaceRoot, configRelPath, language());
    };

    context.subscriptions.push(vscode.workspace.onDidChangeConfiguration((event) => {
        if (event.affectsConfiguration('umaboot.language')
            || event.affectsConfiguration('umaboot.configFile')) {
            refreshLanguageSensitiveUi();
        }
    }));

    // --- File watcher: auto-refresh on yaml changes ------------------------
    if (workspaceRoot) {
        const watcher = vscode.workspace.createFileSystemWatcher(
            new vscode.RelativePattern(workspaceRoot, '{umaboot,crudforge}.yaml'),
        );
        const onAnyChange = (uri: vscode.Uri): void => {
            logger.detail(`[watcher] changed ${uri.fsPath}`);
            treeProvider.refresh();
            codeLensProvider.refresh();
            fileDecorations.refresh([uri]);
            refreshStatusBar(statusBar, workspaceRoot, configRelPath, language());
        };
        watcher.onDidChange(onAnyChange);
        watcher.onDidCreate(onAnyChange);
        watcher.onDidDelete(onAnyChange);
        context.subscriptions.push(watcher);
    }

    // --- CLI shell-out helpers --------------------------------------------
    const cliInvocation = (): { cliPath: string; configPath: string } | undefined => {
        if (!workspaceRoot) {
            vscode.window.showErrorMessage(msg('Umaboot: open a folder first.'));
            return undefined;
        }
        const cfg = vscode.workspace.getConfiguration('umaboot');
        const cliPath = cfg.get<string>('cliPath') ?? 'umaboot';
        const configFile = cfg.get<string>('configFile') ?? 'umaboot.yaml';
        const configPath = path.isAbsolute(configFile)
            ? configFile
            : path.join(workspaceRoot, configFile);
        return { cliPath, configPath };
    };

    const readConfig = (configPath: string): unknown => {
        try {
            return fs.existsSync(configPath)
                ? yaml.load(fs.readFileSync(configPath, 'utf8'))
                : {};
        } catch (err) {
            logger.error('config', `failed to parse ${configPath}: ${err}`);
            return {};
        }
    };

    const run = (sub: 'generate' | 'diff' | 'apply') => async () => {
        const inv = cliInvocation();
        if (!inv) return;
        if (!fs.existsSync(inv.configPath)) {
            vscode.window.showErrorMessage(
                msg('Umaboot: {0} not found. Use "Umaboot: Create umaboot.yaml" to create one.',
                    path.basename(inv.configPath)),
            );
            return;
        }

        let extraArgs: string[] = [];
        if (sub === 'generate') {
            const standaloneArgs = await standaloneExistingPolicyArgs(inv.configPath, workspaceRoot!, language());
            if (standaloneArgs === false) return;
            extraArgs = standaloneArgs;
        }

        const args = [sub, '--config', inv.configPath, ...extraArgs];
        const cli = resolveCliOrShow(inv.cliPath, args, workspaceRoot!);
        if (!cli) return;
        logger.showDetail(true);
        logger.command(`cli ${sub}`, cli.command, cli.args, workspaceRoot!);

        const child = cp.spawn(cli.command, cli.args, {
            cwd: workspaceRoot!,
            shell: cli.shell,
        });
        let stdout = '';
        let stderr = '';
        child.stdout.on('data', (d: Buffer) => {
            const chunk = d.toString();
            stdout += chunk;
            logger.detailRaw(chunk);
        });
        child.stderr.on('data', (d: Buffer) => {
            const chunk = d.toString();
            stderr += chunk;
            logger.detailRaw(chunk);
        });
        child.on('close', async (code: number | null) => {
            logger.result(`cli ${sub}`, code, '', '', {
                includeStdout: false,
                includeStderr: false,
            });
            if (code === 0) {
                vscode.window.showInformationMessage(
                    msg('Umaboot {0}: success', commandLabel(language(), sub)),
                );
                if (sub === 'generate' || sub === 'apply') treeProvider.refresh();
            } else if (code === 1 && sub === 'diff') {
                vscode.window.showInformationMessage(msg('Umaboot diff: changes detected'));
            } else if (code === 1 && sub === 'generate') {
                if ((stdout + stderr).includes('STANDALONE_OUTPUT_EXISTS')) {
                    vscode.window.showWarningMessage(
                        msg('Umaboot standalone: output already looks like an existing project. No files were written.'),
                    );
                    return;
                }
                treeProvider.refresh();
                const action = text(language(), 'Preview / Merge');
                const picked = await vscode.window.showWarningMessage(
                    msg('Umaboot overlay: modified existing files were not overwritten. Open Preview / Merge?'),
                    action,
                );
                if (picked === action) {
                    await vscode.commands.executeCommand('umaboot.previewMerge');
                }
            } else {
                vscode.window.showErrorMessage(
                    msg('Umaboot {0} failed (exit {1}). See output.', commandLabel(language(), sub), code),
                );
            }
        });
    };

    /**
     * Run a CLI subcommand and capture stdout to a string. Used by introspection
     * commands (test-connection, list-tables) where the result is consumed by
     * subsequent UI steps rather than streamed to the user.
     */
    const captureCli = async (subcommand: string, ...extraArgs: string[]): Promise<{ stdout: string; stderr: string; code: number | null }> => {
        const inv = cliInvocation();
        if (!inv) return { stdout: '', stderr: 'no workspace', code: null };
        return await new Promise(resolve => {
            const args = [subcommand, '--config', inv.configPath, ...extraArgs];
            const cli = resolveCliOrMessage(inv.cliPath, args, workspaceRoot!);
            if (!cli.ok) {
                resolve({ stdout: '', stderr: cli.message, code: null });
                return;
            }
            logger.command(`cli ${subcommand}`, cli.command.command, cli.command.args, workspaceRoot!);
            const child = cp.spawn(cli.command.command, cli.command.args, {
                cwd: workspaceRoot!,
                shell: cli.command.shell,
            });
            let stdout = '';
            let stderr = '';
            child.stdout.on('data', (d: Buffer) => { stdout += d.toString(); });
            child.stderr.on('data', (d: Buffer) => { stderr += d.toString(); });
            child.on('close', code => resolve({ stdout, stderr, code }));
        });
    };

    // --- Introspection: Test Connection ------------------------------------
    const testConnection = async (): Promise<void> => {
        const result = await captureCli('test-connection');
        logger.result('test connection', result.code, result.stdout, result.stderr);
        if (result.code === 0) {
            const line = result.stdout.trim().split(/\r?\n/)[0] ?? 'OK';
            vscode.window.showInformationMessage(`Umaboot: ${line}`);
        } else {
            const reason = (result.stderr || result.stdout).trim().split(/\r?\n/)[0]
                ?? text(language(), 'Connection failed');
            vscode.window.showErrorMessage(msg('Umaboot test connection: {0}', reason));
        }
    };

    // --- Introspection: Refresh Tables (lists the tables in a quick pick) --
    const refreshTables = async (): Promise<void> => {
        const inv = cliInvocation();
        if (!inv) return;
        const config = readConfig(inv.configPath);
        const scriptMode = isSchemaFileConfig(config);
        if (scriptMode) {
            const scriptTables = tableNamesFromSchemaFileConfig(config, workspaceRoot!);
            if (scriptTables.length > 0) {
                logger.event('refresh tables', `parsed ${scriptTables.length} CREATE TABLE names from schema file`);
                await showTableQuickPick(scriptTables);
                return;
            }
        }
        const result = await captureCli('list-tables', ...(scriptMode ? ['--raw', '--all'] : []));
        logger.result('refresh tables', result.code, result.stdout, result.stderr);
        if (result.code !== 0) {
            const reason = (result.stderr || result.stdout).trim().split(/\r?\n/)[0]
                ?? text(language(), 'Failed');
            vscode.window.showErrorMessage(msg('Umaboot list-tables: {0}', reason));
            return;
        }
        const tables = result.stdout.trim().split(/\r?\n/).filter(s => s.length > 0);
        if (tables.length === 0) {
            if (scriptMode) {
                const warning = result.stderr.trim().split(/\r?\n/).filter(s => s.length > 0)[0];
                vscode.window.showErrorMessage(warning
                    ? msg('Umaboot list-tables: {0}', warning)
                    : text(language(), 'Umaboot: no tables parsed from schema file. See output.'));
            } else {
                vscode.window.showInformationMessage(
                    msg('Umaboot: schema is empty (or all tables filtered out as junctions).'),
                );
            }
            return;
        }
        await showTableQuickPick(tables);
    };

    const showTableQuickPick = async (tables: string[]): Promise<void> => {
        await vscode.window.showQuickPick(tables, {
            title: `Umaboot: ${tables.length} tables`,
            placeHolder: text(language(), 'Enter to copy a table name to the clipboard'),
            canPickMany: false,
        }).then(picked => {
            if (picked) {
                vscode.env.clipboard.writeText(picked);
                vscode.window.showInformationMessage(msg('Copied "{0}" to clipboard', picked));
            }
        });
    };

    // --- Preview / Merge ---------------------------------------------------
    const previewMerge = async (): Promise<void> => {
        const inv = cliInvocation();
        if (!inv) return;
        if (!fs.existsSync(inv.configPath)) {
            vscode.window.showErrorMessage(
                msg('Umaboot: {0} not found. Use "Umaboot: Create umaboot.yaml" to create one.',
                    path.basename(inv.configPath)),
            );
            return;
        }

        const previewRoot = path.join(context.globalStorageUri.fsPath, 'previews', String(Date.now()));
        const emptyRoot = path.join(previewRoot, '__empty__');
        try {
            fs.mkdirSync(previewRoot, { recursive: true });
        } catch (err) {
            vscode.window.showErrorMessage(
                msg('Umaboot preview: failed to create preview directory: {0}', String(err)),
            );
            return;
        }

        const generateArgs = ['generate', '--config', inv.configPath, '--output', previewRoot];
        const cli = resolveCliOrShow(inv.cliPath, generateArgs, workspaceRoot!);
        if (!cli) return;
        logger.showDetail(true);
        logger.command('preview merge generate', cli.command, cli.args, workspaceRoot!);
        const generated = await runResolvedCliArgs(cli, workspaceRoot!);
        logger.result('preview merge generate', generated.code, generated.stdout, generated.stderr);
        if (generated.code !== 0) {
            const reason = (generated.stderr || generated.stdout).trim().split(/\r?\n/)[0] || 'generation failed';
            vscode.window.showErrorMessage(msg('Umaboot preview failed: {0}', reason));
            return;
        }

        let outputRoot: string;
        try {
            outputRoot = resolveOutputDir(inv.configPath, workspaceRoot!);
        } catch (err) {
            vscode.window.showErrorMessage(
                msg('Umaboot preview: failed to resolve outputDir: {0}', String(err)),
            );
            return;
        }

        const changes = collectPreviewChanges(previewRoot, outputRoot);
        logger.event('preview merge', `found ${changes.length} changed files`);
        if (changes.length === 0) {
            vscode.window.showInformationMessage(msg('Umaboot preview: no generated file changes found.'));
            return;
        }

        const picks = await vscode.window.showQuickPick(
            changes.map(change => ({
                label: `${change.status === 'added' ? '$(add)' : '$(edit)'} ${change.relativePath}`,
                description: change.status,
                detail: change.targetPath,
                change,
            })),
            {
                title: msg('Umaboot Preview / Merge: {0} changed files', changes.length),
                placeHolder: text(language(), 'Select files to preview and optionally accept'),
                canPickMany: true,
            },
        );
        if (!picks || picks.length === 0) return;

        for (const pick of picks) {
            const change = pick.change;
            let left = change.targetPath;
            if (change.status === 'added') {
                left = path.join(emptyRoot, change.relativePath);
                fs.mkdirSync(path.dirname(left), { recursive: true });
                if (!fs.existsSync(left)) fs.writeFileSync(left, '', 'utf8');
            }
            await vscode.commands.executeCommand(
                'vscode.diff',
                vscode.Uri.file(left),
                vscode.Uri.file(change.generatedPath),
                `Umaboot: ${change.relativePath}`,
            );
        }

        const acceptLabel = text(language(), 'Accept Selected');
        const accept = await vscode.window.showWarningMessage(
            msg('Accept {0} selected generated file(s)? This writes generated content to the configured output directory and replaces existing file content.',
                picks.length),
            { modal: true },
            acceptLabel,
        );
        if (accept !== acceptLabel) return;

        for (const pick of picks) {
            fs.mkdirSync(path.dirname(pick.change.targetPath), { recursive: true });
            fs.copyFileSync(pick.change.generatedPath, pick.change.targetPath);
            logger.detail(`[preview merge] accepted ${pick.change.relativePath}`);
        }
        treeProvider.refresh();
        logger.event('preview merge', `accepted ${picks.length} files`);
        vscode.window.showInformationMessage(msg('Umaboot preview: accepted {0} file(s).', picks.length));
    };

    // --- Helper commands ---------------------------------------------------
    const refreshDashboard = (): void => {
        treeProvider.refresh();
        refreshStatusBar(statusBar, workspaceRoot, configRelPath, language());
    };

    const openConfig = async (): Promise<void> => {
        if (!workspaceRoot) return;
        const rel = configRelPath();
        const abs = path.isAbsolute(rel) ? rel : path.join(workspaceRoot, rel);
        if (!fs.existsSync(abs)) {
            const legacy = path.join(workspaceRoot, 'crudforge.yaml');
            if (fs.existsSync(legacy)) {
                await vscode.window.showTextDocument(vscode.Uri.file(legacy));
                return;
            }
            const createLabel = text(language(), 'Create');
            const choice = await vscode.window.showWarningMessage(
                msg('{0} does not exist yet. Create one?', path.basename(abs)),
                createLabel,
                text(language(), 'Cancel'),
            );
            if (choice === createLabel) await createConfig();
            return;
        }
        await vscode.window.showTextDocument(vscode.Uri.file(abs));
    };

    const createConfig = async (): Promise<void> => {
        if (!workspaceRoot) {
            vscode.window.showErrorMessage(msg('Umaboot: open a folder first.'));
            return;
        }
        const rel = configRelPath();
        const abs = path.isAbsolute(rel) ? rel : path.join(workspaceRoot, rel);
        if (fs.existsSync(abs)) {
            await vscode.window.showTextDocument(vscode.Uri.file(abs));
            return;
        }
        const seed = `# Generated by Umaboot. Run \`Umaboot: Generate\` once you've filled this in.
connection:
  mode: host
  type: postgresql            # postgresql | mysql | mariadb | sqlserver | sqlite
  host: localhost:5432
  database: your_db
  params:
  username: postgres
  password: postgres
  schema: public

generation:
  architecture: mvc          # mvc | hexagonal | ddd
  persistence: jpa           # jpa | mybatis | jooq
  buildTool: maven           # maven | gradle
  basePackage: com.example.app
  projectName: example-app
  springBootVersion: 3.3.5
  javaVersion: "17"
  useLombok: true
  mybatis:
    style: xml               # xml | annotation
  jpa:
    useMapStruct: false
  migrations:
    style: none              # none | flyway
  applicationConfig:
    format: yaml             # yaml | properties

  output:
    mode: standalone         # standalone | overlay
    existingPolicy: warn     # warn | overwrite | clean | fail
`;
        fs.writeFileSync(abs, seed, 'utf8');
        logger.event('config', `created ${abs}`);
        await vscode.window.showTextDocument(vscode.Uri.file(abs));
        treeProvider.refresh();
        refreshStatusBar(statusBar, workspaceRoot, configRelPath, language());
    };

    context.subscriptions.push(
        vscode.commands.registerCommand('umaboot.generate', run('generate')),
        vscode.commands.registerCommand('umaboot.diff', run('diff')),
        vscode.commands.registerCommand('umaboot.previewMerge', previewMerge),
        vscode.commands.registerCommand('umaboot.apply', run('apply')),
        vscode.commands.registerCommand('umaboot.testConnection', testConnection),
        vscode.commands.registerCommand('umaboot.refreshTables', refreshTables),
        vscode.commands.registerCommand('umaboot.refreshDashboard', refreshDashboard),
        vscode.commands.registerCommand('umaboot.showSummaryLog', () => logger.showSummary()),
        vscode.commands.registerCommand('umaboot.showDetailLog', () => logger.showDetail()),
        vscode.commands.registerCommand('umaboot.openConfig', openConfig),
        vscode.commands.registerCommand('umaboot.createConfig', createConfig),
        vscode.commands.registerCommand('umaboot.editConfig',
            () => UmabootConfigEditor.open(context, logger, refreshLanguageSensitiveUi)),
        logger,
    );
}

/** Updates the left-side status bar to reflect the current umaboot.yaml. */
function refreshStatusBar(
    item: vscode.StatusBarItem,
    workspaceRoot: string | undefined,
    configRelPath: () => string,
    language: UiLanguage,
): void {
    if (!workspaceRoot) {
        item.hide();
        return;
    }
    const rel = configRelPath();
    const abs = path.isAbsolute(rel) ? rel : path.join(workspaceRoot, rel);
    const legacy = path.join(workspaceRoot, 'crudforge.yaml');
    const exists = fs.existsSync(abs) || fs.existsSync(legacy);
    if (!exists) {
        item.hide();
        return;
    }
    const name = readProjectName(abs) ?? readProjectName(legacy) ?? 'Umaboot';
    item.text = `$(rocket) ${name}`;
    item.tooltip = text(language, 'Umaboot config detected. Click to open the dashboard.');
    item.show();
}

function commandLabel(language: UiLanguage, subcommand: 'generate' | 'diff' | 'apply'): string {
    if (subcommand === 'generate') return text(language, 'Generate');
    if (subcommand === 'diff') return text(language, 'Diff');
    return text(language, 'Apply Generated Files');
}

/** Quick-and-dirty regex to pull projectName from the YAML without importing js-yaml here. */
function readProjectName(filePath: string): string | undefined {
    try {
        if (!fs.existsSync(filePath)) return undefined;
        const raw = fs.readFileSync(filePath, 'utf8');
        const match = raw.match(/^\s{2,}projectName\s*:\s*['"]?([^'"\n\r#]+?)['"]?\s*(#.*)?$/m);
        return match?.[1]?.trim();
    } catch {
        return undefined;
    }
}

/** Re-export for symmetry with VS Code's expected `deactivate` lifecycle. */
export function deactivate(): void {
    /* nothing — disposables in context.subscriptions are cleaned up by the host. */
}

// Suppress unused-import warning when not referenced directly elsewhere.
void isUmabootConfigPath;

interface CliResult {
    stdout: string;
    stderr: string;
    code: number | null;
}

interface PreviewChange {
    relativePath: string;
    generatedPath: string;
    targetPath: string;
    status: 'added' | 'modified';
}

function resolveCliOrShow(
    cliPath: string,
    args: string[],
    workspaceRoot: string,
): ResolvedCliCommand | undefined {
    const resolved = resolveCliOrMessage(cliPath, args, workspaceRoot);
    if (!resolved.ok) {
        vscode.window.showErrorMessage(`Umaboot CLI: ${resolved.message}`);
        return undefined;
    }
    return resolved.command;
}

function resolveCliOrMessage(
    cliPath: string,
    args: string[],
    workspaceRoot: string,
): { ok: true; command: ResolvedCliCommand } | { ok: false; message: string } {
    try {
        return { ok: true, command: resolveCliCommand(cliPath, args, workspaceRoot) };
    } catch (err) {
        if (err instanceof CliResolutionError) {
            return { ok: false, message: err.message };
        }
        return { ok: false, message: String(err) };
    }
}

function runResolvedCliArgs(cli: ResolvedCliCommand, cwd: string): Promise<CliResult> {
    return new Promise(resolve => {
        const child = cp.spawn(cli.command, cli.args, { cwd, shell: cli.shell });
        let stdout = '';
        let stderr = '';
        child.stdout.on('data', (d: Buffer) => { stdout += d.toString(); });
        child.stderr.on('data', (d: Buffer) => { stderr += d.toString(); });
        child.on('close', code => resolve({ stdout, stderr, code }));
    });
}

async function standaloneExistingPolicyArgs(
    configPath: string,
    workspaceRoot: string,
    language: UiLanguage,
): Promise<string[] | false> {
    const raw = fs.existsSync(configPath)
        ? yaml.load(fs.readFileSync(configPath, 'utf8'))
        : {};
    const root = objectValue(raw);
    const gen = objectValue(root.generation);
    const output = objectValue(gen.output);
    const mode = stringValue(output.mode) ?? 'standalone';
    const policy = stringValue(output.existingPolicy) ?? 'warn';
    if (mode !== 'standalone' || policy !== 'warn') return [];

    const outputRoot = resolveOutputDir(configPath, workspaceRoot);
    const markers = standaloneProjectMarkers(outputRoot);
    const hasUmabootMarker = fs.existsSync(path.join(outputRoot, '.umaboot-standalone'));
    const protectedRoot = markers.includes('.git') || markers.includes('umaboot.yaml');
    if (markers.length === 0 || (hasUmabootMarker && !protectedRoot)) return [];

    const overwrite = text(language, 'Overwrite');
    const clean = text(language, 'Clean Output');
    const picked = await vscode.window.showWarningMessage(
        format(language, 'Umaboot standalone: output already looks like an existing project ({0}).',
            markers.join(', ')),
        { modal: true },
        overwrite,
        clean,
    );
    if (picked === overwrite) return ['--existing-policy', 'overwrite'];
    if (picked === clean) return ['--existing-policy', 'clean'];
    return false;
}

function resolveOutputDir(configPath: string, workspaceRoot: string): string {
    const raw = fs.existsSync(configPath)
        ? yaml.load(fs.readFileSync(configPath, 'utf8'))
        : {};
    const root = objectValue(raw);
    const gen = objectValue(root.generation);
    const output = objectValue(gen.output);
    const mode = stringValue(output.mode) ?? 'standalone';
    const configured = stringValue(gen.outputDir) ?? (mode === 'overlay' ? '.' : './generated');
    if (path.isAbsolute(configured)) return path.normalize(configured);
    const base = path.dirname(configPath) || workspaceRoot;
    return path.normalize(path.resolve(base, configured));
}

function standaloneProjectMarkers(outputRoot: string): string[] {
    const candidates = [
        '.git',
        'umaboot.yaml',
        'pom.xml',
        'build.gradle',
        'build.gradle.kts',
        'settings.gradle',
        'settings.gradle.kts',
        'Dockerfile',
        'docker-compose.yml',
        path.join('src', 'main'),
    ];
    return candidates
        .filter(candidate => fs.existsSync(path.join(outputRoot, candidate)))
        .map(candidate => candidate.split(path.sep).join('/'));
}

function collectPreviewChanges(previewRoot: string, outputRoot: string): PreviewChange[] {
    const files = listFilesRecursive(previewRoot)
        .filter(file => !path.relative(previewRoot, file).split(path.sep).includes('__empty__'));
    const changes: PreviewChange[] = [];
    for (const generatedPath of files) {
        const relFs = path.relative(previewRoot, generatedPath);
        const targetPath = path.join(outputRoot, relFs);
        const relativePath = relFs.split(path.sep).join('/');
        if (!fs.existsSync(targetPath)) {
            changes.push({ relativePath, generatedPath, targetPath, status: 'added' });
            continue;
        }
        const generated = fs.readFileSync(generatedPath);
        const current = fs.readFileSync(targetPath);
        if (!generated.equals(current)) {
            changes.push({ relativePath, generatedPath, targetPath, status: 'modified' });
        }
    }
    return changes.sort((a, b) => a.relativePath.localeCompare(b.relativePath));
}

function listFilesRecursive(root: string): string[] {
    if (!fs.existsSync(root)) return [];
    const out: string[] = [];
    for (const entry of fs.readdirSync(root, { withFileTypes: true })) {
        const full = path.join(root, entry.name);
        if (entry.isDirectory()) {
            out.push(...listFilesRecursive(full));
        } else if (entry.isFile()) {
            out.push(full);
        }
    }
    return out;
}

function objectValue(value: unknown): Record<string, unknown> {
    return value && typeof value === 'object' && !Array.isArray(value)
        ? value as Record<string, unknown>
        : {};
}

function stringValue(value: unknown): string | undefined {
    return typeof value === 'string' && value.length > 0 ? value : undefined;
}

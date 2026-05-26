import * as cp from 'child_process';
import * as fs from 'fs';
import * as path from 'path';
import * as vscode from 'vscode';
import { UmabootTreeProvider } from './tree';
import { UmabootCodeLensProvider } from './codeLens';
import { UmabootFileDecorationProvider } from './fileDecoration';
import { UmabootConfigEditor } from './configEditor';
import { isUmabootConfigPath } from './paths';

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
    const channel = vscode.window.createOutputChannel('Umaboot');

    const folder = vscode.workspace.workspaceFolders?.[0];
    const workspaceRoot = folder?.uri.fsPath;

    const configRelPath = (): string =>
        vscode.workspace.getConfiguration('umaboot').get<string>('configFile') ?? 'umaboot.yaml';

    // --- Tree view ---------------------------------------------------------
    const treeProvider = new UmabootTreeProvider(workspaceRoot, configRelPath);
    context.subscriptions.push(
        vscode.window.registerTreeDataProvider('umaboot.dashboard', treeProvider),
    );

    // --- CodeLens on umaboot.yaml ------------------------------------------
    const codeLensProvider = new UmabootCodeLensProvider();
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
    refreshStatusBar(statusBar, workspaceRoot, configRelPath);

    // --- File watcher: auto-refresh on yaml changes ------------------------
    if (workspaceRoot) {
        const watcher = vscode.workspace.createFileSystemWatcher(
            new vscode.RelativePattern(workspaceRoot, '{umaboot,crudforge}.yaml'),
        );
        const onAnyChange = (uri: vscode.Uri): void => {
            treeProvider.refresh();
            codeLensProvider.refresh();
            fileDecorations.refresh([uri]);
            refreshStatusBar(statusBar, workspaceRoot, configRelPath);
        };
        watcher.onDidChange(onAnyChange);
        watcher.onDidCreate(onAnyChange);
        watcher.onDidDelete(onAnyChange);
        context.subscriptions.push(watcher);
    }

    // --- CLI shell-out helpers --------------------------------------------
    const cliInvocation = (): { cli: string; configPath: string } | undefined => {
        if (!workspaceRoot) {
            vscode.window.showErrorMessage('Umaboot: open a folder first.');
            return undefined;
        }
        const cfg = vscode.workspace.getConfiguration('umaboot');
        const cli = cfg.get<string>('cliPath') ?? 'umaboot';
        const configFile = cfg.get<string>('configFile') ?? 'umaboot.yaml';
        const configPath = path.isAbsolute(configFile)
            ? configFile
            : path.join(workspaceRoot, configFile);
        return { cli, configPath };
    };

    const run = (sub: 'generate' | 'diff' | 'apply') => async () => {
        const inv = cliInvocation();
        if (!inv) return;
        if (!fs.existsSync(inv.configPath)) {
            vscode.window.showErrorMessage(
                `Umaboot: ${path.basename(inv.configPath)} not found. Use "Umaboot: Create umaboot.yaml" to create one.`,
            );
            return;
        }

        channel.show(true);
        channel.appendLine(`> ${inv.cli} ${sub} --config ${inv.configPath}`);

        const child = cp.spawn(inv.cli, [sub, '--config', inv.configPath], {
            cwd: workspaceRoot!,
            shell: true,
        });
        child.stdout.on('data', (d: Buffer) => channel.append(d.toString()));
        child.stderr.on('data', (d: Buffer) => channel.append(d.toString()));
        child.on('close', (code: number | null) => {
            channel.appendLine(`\n[exit ${code}]`);
            if (code === 0) {
                vscode.window.showInformationMessage(`Umaboot ${sub}: success`);
                if (sub === 'generate' || sub === 'apply') treeProvider.refresh();
            } else if (code === 1 && sub === 'diff') {
                vscode.window.showInformationMessage('Umaboot diff: changes detected');
            } else {
                vscode.window.showErrorMessage(`Umaboot ${sub} failed (exit ${code}). See output.`);
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
            channel.appendLine(`> ${inv.cli} ${args.join(' ')}`);
            const child = cp.spawn(inv.cli, args, { cwd: workspaceRoot!, shell: true });
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
        channel.append(result.stdout);
        if (result.stderr) channel.append(result.stderr);
        channel.appendLine(`[exit ${result.code}]`);
        if (result.code === 0) {
            const line = result.stdout.trim().split(/\r?\n/)[0] ?? 'OK';
            vscode.window.showInformationMessage(`Umaboot: ${line}`);
        } else {
            const reason = (result.stderr || result.stdout).trim().split(/\r?\n/)[0] ?? 'Connection failed';
            vscode.window.showErrorMessage(`Umaboot test connection: ${reason}`);
        }
    };

    // --- Introspection: Refresh Tables (lists the tables in a quick pick) --
    const refreshTables = async (): Promise<void> => {
        const result = await captureCli('list-tables');
        channel.append(result.stdout);
        if (result.stderr) channel.append(result.stderr);
        channel.appendLine(`[exit ${result.code}]`);
        if (result.code !== 0) {
            const reason = (result.stderr || result.stdout).trim().split(/\r?\n/)[0] ?? 'Failed';
            vscode.window.showErrorMessage(`Umaboot list-tables: ${reason}`);
            return;
        }
        const tables = result.stdout.trim().split(/\r?\n/).filter(s => s.length > 0);
        if (tables.length === 0) {
            vscode.window.showInformationMessage('Umaboot: schema is empty (or all tables filtered out as junctions).');
            return;
        }
        await vscode.window.showQuickPick(tables, {
            title: `Umaboot: ${tables.length} tables`,
            placeHolder: 'Enter to copy a table name to the clipboard',
            canPickMany: false,
        }).then(picked => {
            if (picked) {
                vscode.env.clipboard.writeText(picked);
                vscode.window.showInformationMessage(`Copied "${picked}" to clipboard`);
            }
        });
    };

    // --- Helper commands ---------------------------------------------------
    const refreshDashboard = (): void => {
        treeProvider.refresh();
        refreshStatusBar(statusBar, workspaceRoot, configRelPath);
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
            const choice = await vscode.window.showWarningMessage(
                `${path.basename(abs)} doesn't exist yet. Create one?`,
                'Create',
                'Cancel',
            );
            if (choice === 'Create') await createConfig();
            return;
        }
        await vscode.window.showTextDocument(vscode.Uri.file(abs));
    };

    const createConfig = async (): Promise<void> => {
        if (!workspaceRoot) {
            vscode.window.showErrorMessage('Umaboot: open a folder first.');
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
  url: jdbc:postgresql://localhost:5432/your_db
  username: postgres
  password: postgres
  schema: public

generation:
  architecture: mvc          # mvc | hexagonal | ddd
  persistence: jpa           # jpa | mybatis | jooq
  basePackage: com.example.app
  projectName: example-app
  springBootVersion: 3.3.5
  javaVersion: "17"
  useLombok: true

  output:
    mode: standalone         # standalone | overlay
`;
        fs.writeFileSync(abs, seed, 'utf8');
        await vscode.window.showTextDocument(vscode.Uri.file(abs));
        treeProvider.refresh();
        refreshStatusBar(statusBar, workspaceRoot, configRelPath);
    };

    context.subscriptions.push(
        vscode.commands.registerCommand('umaboot.generate', run('generate')),
        vscode.commands.registerCommand('umaboot.diff', run('diff')),
        vscode.commands.registerCommand('umaboot.apply', run('apply')),
        vscode.commands.registerCommand('umaboot.testConnection', testConnection),
        vscode.commands.registerCommand('umaboot.refreshTables', refreshTables),
        vscode.commands.registerCommand('umaboot.refreshDashboard', refreshDashboard),
        vscode.commands.registerCommand('umaboot.openConfig', openConfig),
        vscode.commands.registerCommand('umaboot.createConfig', createConfig),
        vscode.commands.registerCommand('umaboot.editConfig',
            () => UmabootConfigEditor.open(context, channel)),
        channel,
    );
}

/** Updates the left-side status bar to reflect the current umaboot.yaml. */
function refreshStatusBar(
    item: vscode.StatusBarItem,
    workspaceRoot: string | undefined,
    configRelPath: () => string,
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
    item.tooltip = `Umaboot config detected. Click to open the dashboard.`;
    item.show();
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

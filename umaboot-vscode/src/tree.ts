import * as fs from 'fs';
import * as path from 'path';
import * as vscode from 'vscode';
import * as yaml from 'js-yaml';

/**
 * Tree provider for the Umaboot Activity Bar dashboard.
 *
 * Renders three sections:
 * - **Configuration** — connection URL, basePackage, architecture/persistence
 *   parsed from the workspace's umaboot.yaml.
 * - **Tables** — `tables.include` / `tables.exclude` glob patterns from the
 *   yaml. Live introspection is not in this phase (R.1) — we only show what's
 *   already in the file.
 * - **Actions** — Generate / Diff / Apply leaf items that invoke the matching
 *   commands when clicked.
 */
export class UmabootTreeProvider implements vscode.TreeDataProvider<UmabootNode> {

    private readonly _onDidChangeTreeData = new vscode.EventEmitter<UmabootNode | undefined>();
    readonly onDidChangeTreeData = this._onDidChangeTreeData.event;

    constructor(private readonly workspaceRoot: string | undefined,
                private readonly configRelPath: () => string) {}

    refresh(): void {
        this._onDidChangeTreeData.fire(undefined);
    }

    getTreeItem(element: UmabootNode): vscode.TreeItem {
        return element;
    }

    async getChildren(element?: UmabootNode): Promise<UmabootNode[]> {
        if (!this.workspaceRoot) return [];

        // Top-level: three sections. We render them only when umaboot.yaml is
        // present; otherwise the welcome view kicks in (see package.json
        // viewsWelcome) so the user can create one.
        if (!element) {
            const configPath = this.configFilePath();
            if (!configPath || !fs.existsSync(configPath)) {
                return [];
            }
            const cfg = this.loadConfig(configPath);
            return [
                this.section('Configuration', 'umaboot.section.config', 'gear'),
                this.section('Tables',        'umaboot.section.tables', 'table'),
                this.section('Actions',       'umaboot.section.actions', 'rocket'),
            ];
        }

        const configPath = this.configFilePath();
        if (!configPath) return [];
        const cfg = this.loadConfig(configPath);

        switch (element.contextValue) {
            case 'umaboot.section.config':
                return this.configChildren(cfg);
            case 'umaboot.section.tables':
                return this.tablesChildren(cfg);
            case 'umaboot.section.actions':
                return this.actionsChildren();
            default:
                return [];
        }
    }

    // ----------------------------------------------------------------------

    private section(label: string, contextValue: string, themeIconId: string): UmabootNode {
        const node = new UmabootNode(label, vscode.TreeItemCollapsibleState.Expanded);
        node.contextValue = contextValue;
        node.iconPath = new vscode.ThemeIcon(themeIconId);
        return node;
    }

    private leaf(label: string, description: string | undefined,
                 contextValue: string,
                 commandId?: string,
                 commandArgs: unknown[] = [],
                 themeIconId?: string): UmabootNode {
        const node = new UmabootNode(label, vscode.TreeItemCollapsibleState.None);
        node.contextValue = contextValue;
        if (description) node.description = description;
        if (commandId) {
            node.command = {
                command: commandId,
                title: label,
                arguments: commandArgs,
            };
        }
        if (themeIconId) node.iconPath = new vscode.ThemeIcon(themeIconId);
        return node;
    }

    private configChildren(cfg: ParsedConfig): UmabootNode[] {
        const items: UmabootNode[] = [];
        if (cfg.connectionUrl) {
            items.push(this.leaf('Database', cfg.connectionUrl, 'umaboot.config.url',
                'umaboot.openConfig', [], 'database'));
        }
        if (cfg.architecture || cfg.persistence) {
            const desc = `${cfg.architecture ?? '?'} / ${cfg.persistence ?? '?'}`;
            items.push(this.leaf('Stack', desc, 'umaboot.config.stack',
                'umaboot.openConfig', [], 'symbol-class'));
        }
        if (cfg.basePackage) {
            items.push(this.leaf('Package', cfg.basePackage, 'umaboot.config.package',
                'umaboot.openConfig', [], 'package'));
        }
        if (cfg.javaVersion || cfg.springBootVersion) {
            const desc = `Java ${cfg.javaVersion ?? '?'} • Spring Boot ${cfg.springBootVersion ?? '?'}`;
            items.push(this.leaf('Versions', desc, 'umaboot.config.versions',
                'umaboot.openConfig', [], 'versions'));
        }
        if (cfg.outputMode) {
            items.push(this.leaf('Output mode', cfg.outputMode, 'umaboot.config.outputMode',
                'umaboot.openConfig', [], 'file-directory'));
        }
        if (items.length === 0) {
            items.push(this.leaf('No configuration found', undefined, 'umaboot.config.empty'));
        }
        return items;
    }

    private tablesChildren(cfg: ParsedConfig): UmabootNode[] {
        const items: UmabootNode[] = [];
        if (cfg.includeGlobs.length > 0) {
            items.push(this.leaf('Include', cfg.includeGlobs.join(', '),
                'umaboot.tables.include', 'umaboot.openConfig', [], 'check'));
        }
        if (cfg.excludeGlobs.length > 0) {
            items.push(this.leaf('Exclude', cfg.excludeGlobs.join(', '),
                'umaboot.tables.exclude', 'umaboot.openConfig', [], 'circle-slash'));
        }
        if (items.length === 0) {
            items.push(this.leaf('All tables (no filter set)', undefined, 'umaboot.tables.all',
                'umaboot.openConfig', [], 'list-flat'));
        }
        return items;
    }

    private actionsChildren(): UmabootNode[] {
        return [
            this.leaf('Generate', 'umaboot generate',  'umaboot.action.generate', 'umaboot.generate', [], 'play'),
            this.leaf('Diff',     'umaboot diff',      'umaboot.action.diff',     'umaboot.diff',     [], 'diff'),
            this.leaf('Apply',    'umaboot apply',     'umaboot.action.apply',    'umaboot.apply',    [], 'check'),
        ];
    }

    // ----------------------------------------------------------------------

    /** Resolve the absolute config path for the current workspace. */
    private configFilePath(): string | undefined {
        if (!this.workspaceRoot) return undefined;
        const rel = this.configRelPath();
        const abs = path.isAbsolute(rel) ? rel : path.join(this.workspaceRoot, rel);
        if (fs.existsSync(abs)) return abs;
        // Backwards-compat: pre-rename projects sometimes still have crudforge.yaml.
        const legacy = path.join(this.workspaceRoot, 'crudforge.yaml');
        if (fs.existsSync(legacy)) return legacy;
        return abs; // return the canonical path even if missing — caller checks
    }

    private loadConfig(filePath: string): ParsedConfig {
        try {
            const raw = fs.readFileSync(filePath, 'utf8');
            const doc = yaml.load(raw) as Record<string, unknown> | undefined;
            const conn = (doc?.connection ?? {}) as Record<string, unknown>;
            const gen = (doc?.generation ?? {}) as Record<string, unknown>;
            const tables = (gen?.tables ?? {}) as Record<string, unknown>;
            const output = (gen?.output ?? {}) as Record<string, unknown>;
            return {
                connectionUrl: typeof conn.url === 'string' ? conn.url : undefined,
                architecture:  typeof gen.architecture === 'string' ? gen.architecture : undefined,
                persistence:   typeof gen.persistence === 'string' ? gen.persistence : undefined,
                basePackage:   typeof gen.basePackage === 'string' ? gen.basePackage : undefined,
                javaVersion:   typeof gen.javaVersion === 'string' || typeof gen.javaVersion === 'number'
                                  ? String(gen.javaVersion) : undefined,
                springBootVersion: typeof gen.springBootVersion === 'string' ? gen.springBootVersion : undefined,
                outputMode:    typeof output.mode === 'string' ? output.mode : undefined,
                includeGlobs:  Array.isArray(tables.include) ? tables.include.map(String) : [],
                excludeGlobs:  Array.isArray(tables.exclude) ? tables.exclude.map(String) : [],
            };
        } catch (err) {
            return EMPTY_CONFIG;
        }
    }
}

class UmabootNode extends vscode.TreeItem {
    constructor(label: string, collapsibleState: vscode.TreeItemCollapsibleState) {
        super(label, collapsibleState);
    }
}

interface ParsedConfig {
    connectionUrl?: string;
    architecture?: string;
    persistence?: string;
    basePackage?: string;
    javaVersion?: string;
    springBootVersion?: string;
    outputMode?: string;
    includeGlobs: string[];
    excludeGlobs: string[];
}

const EMPTY_CONFIG: ParsedConfig = { includeGlobs: [], excludeGlobs: [] };

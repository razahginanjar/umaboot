import * as fs from 'fs';
import * as path from 'path';
import * as vscode from 'vscode';
import * as yaml from 'js-yaml';
import { text, UiLanguage } from './i18n';

/**
 * Activity Bar dashboard for the current umaboot.yaml.
 */
export class UmabootTreeProvider implements vscode.TreeDataProvider<UmabootNode> {

    private readonly _onDidChangeTreeData = new vscode.EventEmitter<UmabootNode | undefined>();
    readonly onDidChangeTreeData = this._onDidChangeTreeData.event;

    constructor(
        private readonly workspaceRoot: string | undefined,
        private readonly configRelPath: () => string,
        private readonly language: () => UiLanguage,
    ) {}

    refresh(): void {
        this._onDidChangeTreeData.fire(undefined);
    }

    getTreeItem(element: UmabootNode): vscode.TreeItem {
        return element;
    }

    async getChildren(element?: UmabootNode): Promise<UmabootNode[]> {
        if (!this.workspaceRoot) return [];

        if (!element) {
            const configPath = this.configFilePath();
            if (!configPath || !fs.existsSync(configPath)) return [];
            return [
                this.section('Configuration', 'umaboot.section.config', 'gear'),
                this.section('Tables', 'umaboot.section.tables', 'table'),
                this.section('Actions', 'umaboot.section.actions', 'rocket'),
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

    private section(label: string, contextValue: string, themeIconId: string): UmabootNode {
        const node = new UmabootNode(this.t(label), vscode.TreeItemCollapsibleState.Expanded);
        node.contextValue = contextValue;
        node.iconPath = new vscode.ThemeIcon(themeIconId);
        return node;
    }

    private leaf(
        label: string,
        description: string | undefined,
        contextValue: string,
        commandId?: string,
        commandArgs: unknown[] = [],
        themeIconId?: string,
    ): UmabootNode {
        const translatedLabel = this.t(label);
        const node = new UmabootNode(translatedLabel, vscode.TreeItemCollapsibleState.None);
        node.contextValue = contextValue;
        if (description) node.description = this.t(description);
        if (commandId) {
            node.command = {
                command: commandId,
                title: translatedLabel,
                arguments: commandArgs,
            };
        }
        if (themeIconId) node.iconPath = new vscode.ThemeIcon(themeIconId);
        return node;
    }

    private configChildren(cfg: ParsedConfig): UmabootNode[] {
        const items: UmabootNode[] = [];
        if (cfg.schemaFile) {
            items.push(this.leaf(
                'Schema source',
                short(`${cfg.databaseType ?? 'postgresql'} script: ${cfg.schemaFile}`),
                'umaboot.config.schemaFile',
                'umaboot.openConfig',
                [],
                'file-code',
            ));
        } else if (cfg.connectionLabel) {
            items.push(this.leaf(
                'Database',
                short(cfg.connectionLabel),
                'umaboot.config.url',
                'umaboot.openConfig',
                [],
                'database',
            ));
        }
        if (cfg.architecture || cfg.persistence) {
            const desc = `${cfg.architecture ?? '?'} / ${cfg.persistence ?? '?'} / ${cfg.buildTool ?? 'maven'}`;
            items.push(this.leaf('Stack', desc, 'umaboot.config.stack',
                'umaboot.openConfig', [], 'symbol-class'));
        }
        if (cfg.basePackage) {
            items.push(this.leaf('Package', short(cfg.basePackage), 'umaboot.config.package',
                'umaboot.openConfig', [], 'package'));
        }
        if (cfg.javaVersion || cfg.springBootVersion) {
            const desc = `Java ${cfg.javaVersion ?? '?'} / Spring Boot ${cfg.springBootVersion ?? '?'}`;
            items.push(this.leaf('Versions', desc, 'umaboot.config.versions',
                'umaboot.openConfig', [], 'versions'));
        }
        if (cfg.migrationsStyle || cfg.applicationConfigFormat) {
            const desc = `migrations ${cfg.migrationsStyle ?? 'none'} / config ${cfg.applicationConfigFormat ?? 'yaml'}`;
            items.push(this.leaf('Tooling', desc, 'umaboot.config.tooling',
                'umaboot.openConfig', [], 'tools'));
        }
        if (cfg.outputMode) {
            const desc = cfg.outputDir ? `${cfg.outputMode} -> ${cfg.outputDir}` : cfg.outputMode;
            items.push(this.leaf('Output mode', short(desc), 'umaboot.config.outputMode',
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
            items.push(this.leaf('Include', short(cfg.includeGlobs.join(', ')),
                'umaboot.tables.include', 'umaboot.openConfig', [], 'check'));
        }
        if (cfg.excludeGlobs.length > 0) {
            items.push(this.leaf('Exclude', short(cfg.excludeGlobs.join(', ')),
                'umaboot.tables.exclude', 'umaboot.openConfig', [], 'circle-slash'));
        }
        if (cfg.classNameStripPrefix) {
            items.push(this.leaf('Strip prefix', short(cfg.classNameStripPrefix),
                'umaboot.tables.stripPrefix', 'umaboot.openConfig', [], 'symbol-string'));
        }
        if (items.length === 0) {
            items.push(this.leaf('All tables (no filter set)', undefined, 'umaboot.tables.all',
                'umaboot.openConfig', [], 'list-flat'));
        }
        return items;
    }

    private actionsChildren(): UmabootNode[] {
        return [
            this.leaf('Generate', 'umaboot generate', 'umaboot.action.generate', 'umaboot.generate', [], 'play'),
            this.leaf('Diff', 'umaboot diff', 'umaboot.action.diff', 'umaboot.diff', [], 'diff'),
            this.leaf('Preview / Merge', 'review generated files', 'umaboot.action.previewMerge',
                'umaboot.previewMerge', [], 'diff'),
            this.leaf('Reset Preview / Merge', 'clear active preview choices', 'umaboot.action.resetPreviewMerge',
                'umaboot.resetPreviewMerge', [], 'discard'),
            this.leaf('Apply Generated Files', 'umaboot apply', 'umaboot.action.apply', 'umaboot.apply', [], 'check'),
        ];
    }

    private t(key: string): string {
        return text(this.language(), key);
    }

    private configFilePath(): string | undefined {
        if (!this.workspaceRoot) return undefined;
        const rel = this.configRelPath();
        const abs = path.isAbsolute(rel) ? rel : path.join(this.workspaceRoot, rel);
        if (fs.existsSync(abs)) return abs;
        const legacy = path.join(this.workspaceRoot, 'crudforge.yaml');
        if (fs.existsSync(legacy)) return legacy;
        return abs;
    }

    private loadConfig(filePath: string): ParsedConfig {
        try {
            const raw = fs.readFileSync(filePath, 'utf8');
            const doc = (yaml.load(raw) as Record<string, unknown> | undefined) ?? {};
            const conn = objectValue(doc.connection);
            const gen = objectValue(doc.generation);
            const tables = objectValue(gen.tables);
            const output = objectValue(gen.output);
            const migrations = objectValue(gen.migrations);
            const appConfig = objectValue(gen.applicationConfig);
            const schemaFile = stringValue(doc.schemaFile);
            const databaseType = stringValue(doc.schemaDialect)
                ?? stringValue(conn.type)
                ?? deriveTypeFromUrl(conn.url);

            return {
                schemaFile,
                databaseType,
                connectionLabel: schemaFile ? undefined : connectionLabel(conn),
                architecture: stringValue(gen.architecture),
                persistence: stringValue(gen.persistence),
                buildTool: stringValue(gen.buildTool),
                basePackage: stringValue(gen.basePackage),
                javaVersion: stringOrNumber(gen.javaVersion),
                springBootVersion: stringValue(gen.springBootVersion),
                outputMode: stringValue(output.mode),
                outputDir: stringValue(gen.outputDir),
                migrationsStyle: stringValue(migrations.style),
                applicationConfigFormat: stringValue(appConfig.format),
                classNameStripPrefix: stringValue(tables.classNameStripPrefix),
                includeGlobs: Array.isArray(tables.include) ? tables.include.map(String) : [],
                excludeGlobs: Array.isArray(tables.exclude) ? tables.exclude.map(String) : [],
            };
        } catch {
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
    schemaFile?: string;
    databaseType?: string;
    connectionLabel?: string;
    architecture?: string;
    persistence?: string;
    buildTool?: string;
    basePackage?: string;
    javaVersion?: string;
    springBootVersion?: string;
    outputMode?: string;
    outputDir?: string;
    migrationsStyle?: string;
    applicationConfigFormat?: string;
    classNameStripPrefix?: string;
    includeGlobs: string[];
    excludeGlobs: string[];
}

const EMPTY_CONFIG: ParsedConfig = { includeGlobs: [], excludeGlobs: [] };

function objectValue(value: unknown): Record<string, unknown> {
    return value && typeof value === 'object' && !Array.isArray(value)
        ? value as Record<string, unknown>
        : {};
}

function stringValue(value: unknown): string | undefined {
    return typeof value === 'string' && value.length > 0 ? value : undefined;
}

function stringOrNumber(value: unknown): string | undefined {
    if (typeof value === 'string') return value;
    if (typeof value === 'number') return String(value);
    return undefined;
}

function short(value: string | undefined): string | undefined {
    if (!value) return value;
    return value.length > 45 ? `${value.slice(0, 45)}...` : value;
}

function connectionLabel(conn: Record<string, unknown>): string | undefined {
    const url = stringValue(conn.url);
    if (url) return url;
    if (stringValue(conn.mode) !== 'host') return undefined;

    const type = stringValue(conn.type) ?? 'postgresql';
    const host = stringValue(conn.host) ?? '';
    const database = stringValue(conn.database) ?? '';
    if (type === 'sqlite') return database ? `jdbc:sqlite:${database}` : undefined;
    if (type === 'sqlserver') {
        return host && database ? `jdbc:sqlserver://${host};databaseName=${database}` : undefined;
    }
    return host && database ? `jdbc:${type}://${host}/${database}` : undefined;
}

function deriveTypeFromUrl(value: unknown): string | undefined {
    if (typeof value !== 'string') return undefined;
    if (value.startsWith('jdbc:mariadb:')) return 'mariadb';
    if (value.startsWith('jdbc:mysql:')) return 'mysql';
    if (value.startsWith('jdbc:sqlserver:')) return 'sqlserver';
    if (value.startsWith('jdbc:sqlite:')) return 'sqlite';
    if (value.startsWith('jdbc:postgresql:')) return 'postgresql';
    return undefined;
}

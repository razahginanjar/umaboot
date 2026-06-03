import * as fs from 'fs';
import * as path from 'path';

const CREATE_TABLE_NAME = /\bCREATE\s+(?:OR\s+REPLACE\s+)?(?:UNLOGGED\s+)?(?:(?:GLOBAL|LOCAL)\s+TEMPORARY\s+|TEMPORARY\s+|TEMP\s+)?TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?([^\s(]+)/gi;

export function isSchemaFileConfig(config: unknown): boolean {
    return Boolean(schemaFileFromConfig(config));
}

export function tableNamesFromSchemaFileConfig(config: unknown, workspaceRoot: string): string[] {
    const schemaFile = schemaFileFromConfig(config);
    if (!schemaFile) return [];
    const filePath = path.isAbsolute(schemaFile)
        ? schemaFile
        : path.resolve(workspaceRoot, schemaFile);
    try {
        return extractCreateTableNames(fs.readFileSync(filePath, 'utf8'));
    } catch {
        return [];
    }
}

export function extractCreateTableNames(sql: string): string[] {
    const text = stripSqlComments(sql);
    const seen = new Set<string>();
    const names: string[] = [];
    for (const match of text.matchAll(CREATE_TABLE_NAME)) {
        const name = normalizeTableName(match[1]);
        if (!name || seen.has(name)) continue;
        seen.add(name);
        names.push(name);
    }
    return names;
}

function schemaFileFromConfig(config: unknown): string | undefined {
    const root = objectValue(config);
    const rootSchemaFile = stringValue(root.schemaFile);
    if (rootSchemaFile) return rootSchemaFile;
    return stringValue(objectValue(root.generation).schemaFile);
}

function stripSqlComments(sql: string): string {
    return sql
        .replace(/\/\*[\s\S]*?\*\//g, ' ')
        .replace(/--.*$/gm, ' ');
}

function normalizeTableName(raw: string | undefined): string {
    let name = (raw ?? '').trim();
    while (name.endsWith(';') || name.endsWith(',')) {
        name = name.slice(0, -1).trim();
    }
    name = name.replace(/[\[\]`"]/g, '');
    const dot = name.lastIndexOf('.');
    if (dot >= 0) {
        name = name.slice(dot + 1);
    }
    return name.trim();
}

function objectValue(value: unknown): Record<string, unknown> {
    return value && typeof value === 'object' && !Array.isArray(value)
        ? value as Record<string, unknown>
        : {};
}

function stringValue(value: unknown): string | undefined {
    return typeof value === 'string' && value.trim().length > 0 ? value.trim() : undefined;
}

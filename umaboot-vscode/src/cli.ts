import * as fs from 'fs';
import * as path from 'path';

export interface ResolvedCliCommand {
    command: string;
    args: string[];
    shell: boolean;
}

export class CliResolutionError extends Error {
    constructor(message: string) {
        super(message);
        this.name = 'CliResolutionError';
    }
}

export function resolveCliCommand(
    configuredCli: string | undefined,
    invocationArgs: string[],
    workspaceRoot?: string,
): ResolvedCliCommand {
    const configured = (configuredCli ?? '').trim() || 'umaboot';
    const directPath = resolvePathCandidate(unquote(configured), workspaceRoot);

    if (isJarPath(directPath)) {
        return javaJarCommand(directPath, invocationArgs);
    }

    if (isExistingDirectory(directPath)) {
        const jarPath = findCliJar(directPath);
        if (!jarPath) {
            throw new CliResolutionError(
                `No Umaboot CLI jar found in ${directPath}. Expected umaboot.jar or umaboot-cli-*.jar.`,
            );
        }
        return javaJarCommand(jarPath, invocationArgs);
    }

    const tokens = splitCommandLine(configured);
    if (tokens.length === 0) {
        return { command: 'umaboot', args: invocationArgs, shell: true };
    }

    const command = tokens[0];
    const prefixArgs = tokens.slice(1);
    if (isJavaJarInvocation(command, prefixArgs)) {
        return {
            command,
            args: [...prefixArgs, ...invocationArgs],
            shell: false,
        };
    }

    return {
        command,
        args: [...prefixArgs, ...invocationArgs],
        shell: shouldUseShell(command),
    };
}

function javaJarCommand(jarPath: string, invocationArgs: string[]): ResolvedCliCommand {
    return {
        command: 'java',
        args: ['-jar', jarPath, ...invocationArgs],
        shell: false,
    };
}

function isJavaJarInvocation(command: string, args: string[]): boolean {
    return path.basename(command).toLowerCase().replace(/\.exe$/, '') === 'java'
        && args.some(arg => arg.toLowerCase() === '-jar');
}

function shouldUseShell(command: string): boolean {
    const ext = path.extname(unquote(command)).toLowerCase();
    return ext !== '.exe' && ext !== '.jar';
}

function isJarPath(value: string): boolean {
    return path.extname(value).toLowerCase() === '.jar';
}

function isExistingDirectory(value: string): boolean {
    try {
        return fs.existsSync(value) && fs.statSync(value).isDirectory();
    } catch {
        return false;
    }
}

function findCliJar(directory: string): string | undefined {
    let names: string[];
    try {
        names = fs.readdirSync(directory);
    } catch {
        return undefined;
    }

    const ranked = names
        .filter(name => name.toLowerCase().endsWith('.jar'))
        .map(name => path.join(directory, name))
        .map(filePath => ({ filePath, rank: jarRank(path.basename(filePath)), mtime: fileMtime(filePath) }))
        .filter(item => item.rank < Number.MAX_SAFE_INTEGER)
        .sort((a, b) => a.rank - b.rank
            || b.mtime - a.mtime
            || a.filePath.localeCompare(b.filePath));

    return ranked[0]?.filePath;
}

function jarRank(fileName: string): number {
    const lower = fileName.toLowerCase();
    if (lower === 'umaboot.jar') return 0;
    if (/^umaboot-cli-.*-shaded\.jar$/.test(lower)) return 1;
    if (/^umaboot-cli-.*\.jar$/.test(lower)) return 2;
    if (/^umaboot.*\.jar$/.test(lower)) return 3;
    return Number.MAX_SAFE_INTEGER;
}

function fileMtime(filePath: string): number {
    try {
        return fs.statSync(filePath).mtimeMs;
    } catch {
        return 0;
    }
}

function resolvePathCandidate(value: string, workspaceRoot?: string): string {
    if (path.isAbsolute(value)) return path.normalize(value);
    if (workspaceRoot && looksLikePath(value)) {
        return path.resolve(workspaceRoot, value);
    }
    return value;
}

function looksLikePath(value: string): boolean {
    return value.startsWith('.')
        || value.includes('/')
        || value.includes('\\');
}

function unquote(value: string): string {
    const trimmed = value.trim();
    if ((trimmed.startsWith('"') && trimmed.endsWith('"'))
        || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
        return trimmed.slice(1, -1);
    }
    return trimmed;
}

function splitCommandLine(value: string): string[] {
    const tokens: string[] = [];
    let current = '';
    let quote: '"' | "'" | undefined;

    for (let i = 0; i < value.length; i++) {
        const ch = value[i];
        if (quote) {
            if (ch === quote) {
                quote = undefined;
            } else {
                current += ch;
            }
            continue;
        }
        if (ch === '"' || ch === "'") {
            quote = ch;
            continue;
        }
        if (/\s/.test(ch)) {
            if (current.length > 0) {
                tokens.push(current);
                current = '';
            }
            continue;
        }
        current += ch;
    }

    if (current.length > 0) {
        tokens.push(current);
    }
    return tokens;
}

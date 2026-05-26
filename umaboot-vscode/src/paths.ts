/**
 * Path-related helpers shared across the extension. Kept tiny — VS Code's
 * own {@link import('vscode').workspace.asRelativePath} handles most cases,
 * but file decoration / code lens providers need a quick "is this our YAML"
 * check that doesn't depend on the active workspace.
 */

export function isUmabootConfigPath(p: string): boolean {
    const base = baseName(p).toLowerCase();
    return base === 'umaboot.yaml' || base === 'umaboot.yml'
        || base === 'crudforge.yaml' || base === 'crudforge.yml';
}

export function baseName(p: string): string {
    const i = Math.max(p.lastIndexOf('/'), p.lastIndexOf('\\'));
    return i < 0 ? p : p.substring(i + 1);
}

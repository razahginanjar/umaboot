import * as vscode from 'vscode';

type AutoShowMode = 'never' | 'summary' | 'detail' | 'detailOnError';

interface ResultOptions {
    includeStdout?: boolean;
    includeStderr?: boolean;
}

export class UmabootLogger implements vscode.Disposable {
    private readonly summaryChannel = vscode.window.createOutputChannel('Umaboot Summary');
    private readonly detailChannel = vscode.window.createOutputChannel('Umaboot Detail');

    summary(message: string): void {
        this.summaryChannel.appendLine(`${timestamp()} ${message}`);
    }

    detail(message: string): void {
        this.detailChannel.appendLine(`${timestamp()} ${message}`);
    }

    detailRaw(text: string): void {
        if (text) this.detailChannel.append(text);
    }

    command(scope: string, command: string, args: string[], cwd?: string): void {
        this.summary(`${scope}: started`);
        this.detail(`> ${command} ${args.join(' ')}`);
        if (cwd) this.detail(`[cwd] ${cwd}`);
    }

    result(
        scope: string,
        code: number | null,
        stdout = '',
        stderr = '',
        options: ResultOptions = {},
    ): void {
        const includeStdout = options.includeStdout !== false;
        const includeStderr = options.includeStderr !== false;
        if (includeStdout) this.detailRaw(stdout);
        if (includeStderr) this.detailRaw(stderr);
        this.detail(`[exit ${code}]`);
        const failed = code !== 0;
        this.summary(`${scope}: ${failed ? 'failed' : 'finished'} (exit ${code})`);
        this.autoShow(failed);
    }

    event(scope: string, message: string): void {
        this.summary(`${scope}: ${message}`);
        this.detail(`[${scope}] ${message}`);
    }

    error(scope: string, message: string): void {
        this.summary(`${scope}: error - ${message}`);
        this.detail(`[${scope}] error - ${message}`);
        this.autoShow(true);
    }

    showSummary(preserveFocus = false): void {
        this.summaryChannel.show(preserveFocus);
    }

    showDetail(preserveFocus = false): void {
        this.detailChannel.show(preserveFocus);
    }

    dispose(): void {
        this.summaryChannel.dispose();
        this.detailChannel.dispose();
    }

    private autoShow(failed: boolean): void {
        const mode = vscode.workspace
            .getConfiguration('umaboot')
            .get<AutoShowMode>('logging.autoShow', 'detailOnError');
        if (mode === 'summary') {
            this.showSummary(true);
        } else if (mode === 'detail' || (mode === 'detailOnError' && failed)) {
            this.showDetail(true);
        }
    }
}

function timestamp(): string {
    return new Date().toISOString();
}

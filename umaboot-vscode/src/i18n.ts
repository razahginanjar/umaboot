import * as vscode from 'vscode';

export type UiLanguage = 'en' | 'id' | 'ja';

const LANGUAGE_STATE_KEY = 'umaboot.uiLanguage';

const TRANSLATIONS: Record<'id' | 'ja', Record<string, string>> = {
    id: {
        'Dashboard': 'Dashboard',
        'Configuration': 'Konfigurasi',
        'Tables': 'Tabel',
        'Actions': 'Aksi',
        'Schema source': 'Sumber skema',
        'Database': 'Database',
        'Stack': 'Stack',
        'Package': 'Package',
        'Versions': 'Versi',
        'Tooling': 'Tooling',
        'Output mode': 'Mode output',
        'No configuration found': 'Konfigurasi tidak ditemukan',
        'Include': 'Include',
        'Exclude': 'Exclude',
        'Strip prefix': 'Buang prefix',
        'All tables (no filter set)': 'Semua tabel (tanpa filter)',
        'Generate': 'Generate',
        'Diff': 'Diff',
        'Preview / Merge': 'Preview / Merge',
        'Apply Generated Files': 'Terapkan File Generated',
        'Test Connection': 'Tes Koneksi',
        'Refresh Tables': 'Muat Ulang Tabel',
        'review generated files': 'review file generated',
        'Run Umaboot generate against this config': 'Jalankan Umaboot generate untuk config ini',
        'Show pending changes without writing': 'Lihat perubahan tanpa menulis file',
        'Preview generated files and accept selected changes': 'Preview file generated dan terima perubahan yang dipilih',
        'Apply generated files preserving protected regions': 'Terapkan file generated sambil menjaga protected region',
        'Verify the JDBC URL is reachable': 'Pastikan JDBC URL bisa diakses',
        'Umaboot: open a folder first.': 'Umaboot: buka folder terlebih dahulu.',
        'Umaboot: {0} not found. Use "Umaboot: Create umaboot.yaml" to create one.': 'Umaboot: {0} tidak ditemukan. Gunakan "Umaboot: Create umaboot.yaml" untuk membuatnya.',
        'Umaboot {0}: success': 'Umaboot {0}: sukses',
        'Umaboot diff: changes detected': 'Umaboot diff: perubahan terdeteksi',
        'Umaboot {0} failed (exit {1}). See output.': 'Umaboot {0} gagal (exit {1}). Lihat output.',
        'Connection failed': 'Koneksi gagal',
        'Umaboot test connection: {0}': 'Umaboot tes koneksi: {0}',
        'Failed': 'Gagal',
        'Umaboot list-tables: {0}': 'Umaboot list-tables: {0}',
        'Umaboot: schema is empty (or all tables filtered out as junctions).': 'Umaboot: skema kosong (atau semua tabel terfilter sebagai junction).',
        'Enter to copy a table name to the clipboard': 'Enter untuk menyalin nama tabel ke clipboard',
        'Copied "{0}" to clipboard': 'Menyalin "{0}" ke clipboard',
        'Umaboot preview: failed to create preview directory: {0}': 'Umaboot preview: gagal membuat direktori preview: {0}',
        'Umaboot preview failed: {0}': 'Umaboot preview gagal: {0}',
        'Umaboot preview: failed to resolve outputDir: {0}': 'Umaboot preview: gagal membaca outputDir: {0}',
        'Umaboot preview: no generated file changes found.': 'Umaboot preview: tidak ada perubahan file generated.',
        'Umaboot Preview / Merge: {0} changed files': 'Umaboot Preview / Merge: {0} file berubah',
        'Select files to preview and optionally accept': 'Pilih file untuk preview dan opsional diterima',
        'Accept {0} selected generated file(s)? This writes the generated file content to the configured output directory.': 'Terima {0} file generated yang dipilih? Ini akan menulis konten generated ke output directory.',
        'Accept Selected': 'Terima Pilihan',
        'Umaboot preview: accepted {0} file(s).': 'Umaboot preview: menerima {0} file.',
        '{0} does not exist yet. Create one?': '{0} belum ada. Buat sekarang?',
        'Create': 'Buat',
        'Cancel': 'Batal',
        'Umaboot config detected. Click to open the dashboard.': 'Config Umaboot terdeteksi. Klik untuk membuka dashboard.',
        'Umaboot: failed to parse {0}; opening with empty defaults.': 'Umaboot: gagal parse {0}; membuka dengan default kosong.',
        'Umaboot: saved {0}.': 'Umaboot: menyimpan {0}.',
        'Umaboot: save failed; {0}': 'Umaboot: gagal menyimpan; {0}',
        'failed to write temp config: {0}': 'gagal menulis config sementara: {0}',
        'Select Schema SQL File': 'Pilih File SQL Skema',
    },
    ja: {
        'Dashboard': 'ダッシュボード',
        'Configuration': '設定',
        'Tables': 'テーブル',
        'Actions': 'アクション',
        'Schema source': 'スキーマソース',
        'Database': 'データベース',
        'Stack': 'スタック',
        'Package': 'パッケージ',
        'Versions': 'バージョン',
        'Tooling': 'ツール',
        'Output mode': '出力モード',
        'No configuration found': '設定が見つかりません',
        'Include': 'Include',
        'Exclude': 'Exclude',
        'Strip prefix': 'プレフィックス削除',
        'All tables (no filter set)': 'すべてのテーブル (フィルターなし)',
        'Generate': '生成',
        'Diff': '差分',
        'Preview / Merge': 'プレビュー / マージ',
        'Apply Generated Files': '生成ファイルを適用',
        'Test Connection': '接続テスト',
        'Refresh Tables': 'テーブル更新',
        'review generated files': '生成ファイルを確認',
        'Run Umaboot generate against this config': 'この設定で Umaboot generate を実行します',
        'Show pending changes without writing': '書き込まずに変更を表示します',
        'Preview generated files and accept selected changes': '生成ファイルをプレビューして選択した変更を受け入れます',
        'Apply generated files preserving protected regions': 'protected region を維持して生成ファイルを適用します',
        'Verify the JDBC URL is reachable': 'JDBC URL に接続できるか確認します',
        'Umaboot: open a folder first.': 'Umaboot: 先にフォルダーを開いてください。',
        'Umaboot: {0} not found. Use "Umaboot: Create umaboot.yaml" to create one.': 'Umaboot: {0} が見つかりません。"Umaboot: Create umaboot.yaml" で作成してください。',
        'Umaboot {0}: success': 'Umaboot {0}: 成功',
        'Umaboot diff: changes detected': 'Umaboot diff: 変更が検出されました',
        'Umaboot {0} failed (exit {1}). See output.': 'Umaboot {0} が失敗しました (exit {1})。出力を確認してください。',
        'Connection failed': '接続に失敗しました',
        'Umaboot test connection: {0}': 'Umaboot 接続テスト: {0}',
        'Failed': '失敗',
        'Umaboot list-tables: {0}': 'Umaboot list-tables: {0}',
        'Umaboot: schema is empty (or all tables filtered out as junctions).': 'Umaboot: スキーマが空です (またはすべてのテーブルが junction として除外されました)。',
        'Enter to copy a table name to the clipboard': 'Enter でテーブル名をクリップボードにコピー',
        'Copied "{0}" to clipboard': '"{0}" をクリップボードにコピーしました',
        'Umaboot preview: failed to create preview directory: {0}': 'Umaboot preview: プレビューディレクトリを作成できませんでした: {0}',
        'Umaboot preview failed: {0}': 'Umaboot preview が失敗しました: {0}',
        'Umaboot preview: failed to resolve outputDir: {0}': 'Umaboot preview: outputDir を解決できませんでした: {0}',
        'Umaboot preview: no generated file changes found.': 'Umaboot preview: 生成ファイルの変更はありません。',
        'Umaboot Preview / Merge: {0} changed files': 'Umaboot Preview / Merge: {0} 件の変更ファイル',
        'Select files to preview and optionally accept': 'プレビューして必要に応じて受け入れるファイルを選択',
        'Accept {0} selected generated file(s)? This writes the generated file content to the configured output directory.': '選択した生成ファイル {0} 件を受け入れますか？設定された出力ディレクトリに書き込みます。',
        'Accept Selected': '選択を受け入れる',
        'Umaboot preview: accepted {0} file(s).': 'Umaboot preview: {0} ファイルを受け入れました。',
        '{0} does not exist yet. Create one?': '{0} はまだ存在しません。作成しますか？',
        'Create': '作成',
        'Cancel': 'キャンセル',
        'Umaboot config detected. Click to open the dashboard.': 'Umaboot 設定を検出しました。クリックしてダッシュボードを開きます。',
        'Umaboot: failed to parse {0}; opening with empty defaults.': 'Umaboot: {0} の解析に失敗しました。空の既定値で開きます。',
        'Umaboot: saved {0}.': 'Umaboot: {0} を保存しました。',
        'Umaboot: save failed; {0}': 'Umaboot: 保存に失敗しました; {0}',
        'failed to write temp config: {0}': '一時設定の書き込みに失敗しました: {0}',
        'Select Schema SQL File': 'スキーマ SQL ファイルを選択',
    },
};

export function isLanguage(value: unknown): value is UiLanguage {
    return value === 'en' || value === 'id' || value === 'ja';
}

export function normalizeLanguage(value: unknown): UiLanguage {
    if (isLanguage(value)) return value;
    const raw = String(value || '').toLowerCase();
    if (raw.startsWith('id') || raw.includes('indonesia')) return 'id';
    if (raw.startsWith('ja') || raw.includes('japanese')) return 'ja';
    return 'en';
}

export function getLanguage(context?: vscode.ExtensionContext): UiLanguage {
    const stored = context?.workspaceState.get<string>(LANGUAGE_STATE_KEY);
    if (isLanguage(stored)) return stored;
    return normalizeLanguage(vscode.workspace.getConfiguration('umaboot').get<string>('language'));
}

export async function setLanguage(context: vscode.ExtensionContext, language: unknown): Promise<UiLanguage> {
    const normalized = normalizeLanguage(language);
    await context.workspaceState.update(LANGUAGE_STATE_KEY, normalized);
    return normalized;
}

export function text(language: UiLanguage, key: string): string {
    if (language === 'en') return key;
    return TRANSLATIONS[language][key] ?? key;
}

export function format(language: UiLanguage, key: string, ...args: Array<string | number | null | undefined>): string {
    let out = text(language, key);
    args.forEach((arg, index) => {
        out = out.replace(new RegExp(`\\{${index}\\}`, 'g'), String(arg ?? ''));
    });
    return out;
}

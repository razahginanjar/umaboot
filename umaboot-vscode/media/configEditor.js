/*
 * Umaboot configuration webview script.
 *
 * The host owns YAML I/O. This form edits a cloned config object so fields not
 * exposed directly by the webview, such as DDD settings and table overrides,
 * survive a save.
 */
(function () {
    'use strict';
    /* global acquireVsCodeApi */
    const vscode = acquireVsCodeApi();

    let lastLoaded = null;

    const $ = (id) => document.getElementById(id);
    const form = $('config-form');
    const status = $('status');
    const helpPanel = $('help-panel');
    const connectionResult = $('connection-result');
    const uiLanguage = $('uiLanguage');

    const sourceHost = $('source-host');
    const sourceUrl = $('source-url');
    const sourceScript = $('source-script');
    const liveConnectionFields = $('live-connection-fields');
    const securityJwtBox = $('security-jwt');
    const securityUsersBox = $('security-users');

    const HELP = {
        connectionSource: ['Source', 'Chooses whether Umaboot builds a JDBC URL from host fields, uses a raw JDBC URL, or parses a SQL schema file.', 'script'],
        databaseType: ['Database type', 'Database dialect used for JDBC URLs, scripts, dependencies, Docker, CI, migrations, and generated tests.', 'mysql'],
        connectionHost: ['Host', 'Host and port for live database access when source is host.', 'localhost:5432'],
        connectionDatabase: ['Database', 'Database name. For SQLite this is the database file path or :memory:.', 'inventory'],
        connectionParams: ['Parameters', 'Optional JDBC query parameters. Do not start with a question mark.', 'sslmode=disable'],
        connectionUrl: ['JDBC URL', 'Full JDBC URL used when source is url.', 'jdbc:mysql://localhost:3306/inventory'],
        schemaFile: ['Schema file', 'SQL DDL file parsed when source is script. Workspace-relative paths stay portable.', 'src/main/resources/schema.sql'],
        connectionUsername: ['Username', 'Database username for live connection modes.', 'postgres'],
        connectionPassword: ['Password', 'Database password for live connection modes.', 'postgres'],
        connectionSchema: ['Schema', 'Schema name for PostgreSQL and SQL Server introspection. SQLite ignores this field.', 'public'],
        architecture: ['Architecture', 'Generated project structure and package layout.', 'mvc'],
        persistence: ['Persistence', 'Repository and persistence technology for the generated project.', 'jpa'],
        buildTool: ['Build tool', 'Generated build system and CI command style.', 'gradle'],
        mybatisStyle: ['MyBatis style', 'Controls whether MyBatis SQL is generated in XML mapper files or annotations.', 'xml'],
        useMapStruct: ['Use MapStruct', 'Adds MapStruct mapper generation for JPA DTO/entity conversion.', 'enabled for JPA MVC projects'],
        basePackage: ['Base package', 'Root Java package for generated source files.', 'com.example.inventory'],
        projectName: ['Project name', 'Application artifact/name used in build files and generated output.', 'inventory-service'],
        projectGroup: ['Project group', 'Build group ID or organization coordinate.', 'com.example'],
        javaVersion: ['Java version', 'Java language version for generated source compatibility and build configuration.', '17'],
        springBootVersion: ['Spring Boot version', 'Spring Boot version used in generated Maven or Gradle files.', '3.3.5'],
        useLombok: ['Use Lombok', 'Adds Lombok and uses Lombok annotations where supported.', 'enabled'],
        injectionStyle: ['Injection', 'Controls how dependencies are injected in generated Spring components.', 'constructor'],
        validationStyle: ['Validation', 'Controls generated request validation style.', 'jakarta'],
        dtoStyle: ['DTO style', 'Controls whether DTOs are classes or records when supported by Java/Spring.', 'class'],
        dtoShape: ['DTO shape', 'Controls whether request and response DTOs are separate or shared.', 'separate'],
        exceptionStyle: ['Exception', 'Controls generated API error response style.', 'problemdetail'],
        openapiStyle: ['OpenAPI', 'Controls generated OpenAPI support.', 'annotation'],
        paginationStyle: ['Pagination', 'Controls generated list endpoint pagination style.', 'offset'],
        auditEnabled: ['Audit fields', 'Detects conventional audit columns such as created_at and updated_by.', 'created_at'],
        softDeleteEnabled: ['Soft delete', 'Detects soft-delete columns and adjusts generated behavior.', 'is_deleted'],
        dockerEnabled: ['Docker', 'Generates Dockerfile and docker-compose.yml for the app and selected database.', 'enabled for MySQL'],
        ciStyle: ['CI pipeline', 'Generates CI workflow files for the selected provider.', 'github'],
        migrationsStyle: ['Migrations', 'Adds database migration support to the generated project.', 'flyway'],
        loggingStyle: ['Logging', 'Controls generated logging format.', 'json'],
        applicationConfigFormat: ['App config format', 'Chooses YAML or properties for generated application config.', 'yaml'],
        loggingCorrelationId: ['Correlation ID', 'Adds a request filter that copies X-Correlation-Id into MDC.', 'X-Correlation-Id=order-123'],
        testsEnabled: ['Integration tests', 'Generates Spring Boot integration tests backed by Testcontainers where supported.', 'enabled'],
        securityStyle: ['Security', 'Controls generated Spring Security setup.', 'jwt'],
        jwtSecret: ['JWT secret', 'Secret used to sign JWT tokens. Externalize before deploying.', 'SPRING_SECURITY_JWT_SECRET'],
        jwtExpirationMinutes: ['JWT expiration', 'Token lifetime in minutes.', '60'],
        securityUsers: ['Users', 'One user per line using username:password:ROLE1,ROLE2.', 'admin:admin:ADMIN,USER'],
        outputMode: ['Output mode', 'Controls whether generation creates a standalone project or overlays files.', 'standalone'],
        useProjectDirectory: ['Use project directory', 'Writes generated files next to umaboot.yaml by setting outputDir to dot.', 'enabled'],
        outputDir: ['Directory', 'Output directory when project-directory output is disabled.', 'generated/inventory-service'],
        classNameStripPrefix: ['Class-name strip prefix', 'Removes a common table prefix before generating Java class names.', 'tbl_users becomes User'],
        tablesInclude: ['Include globs', 'Only matching tables are generated when this list is not empty.', 'customer*'],
        tablesExclude: ['Exclude globs', 'Matching tables are skipped even if included.', 'audit_*'],
        tableOverrides: ['Table overrides', 'JSON object for per-table className and per-column javaType overrides.', '{"users":{"className":"AccountUser"}}'],
    };

    const TRANSLATIONS = {
        id: {
            'Umaboot configuration': 'Konfigurasi Umaboot',
            'Language': 'Bahasa',
            'Connection': 'Koneksi',
            'Source': 'Sumber',
            'Database type': 'Tipe database',
            'Host': 'Host',
            'Database': 'Database',
            'Parameters': 'Parameter',
            'JDBC URL': 'JDBC URL',
            'Schema file': 'File skema',
            'Browse...': 'Pilih...',
            'Username': 'Nama pengguna',
            'Password': 'Kata sandi',
            'Schema': 'Skema',
            'Test Connection': 'Tes Koneksi',
            'Refresh Tables': 'Muat Ulang Tabel',
            'Project': 'Proyek',
            'Architecture': 'Arsitektur',
            'Persistence': 'Persistensi',
            'Build tool': 'Build tool',
            'MyBatis style': 'Gaya MyBatis',
            'Use MapStruct': 'Gunakan MapStruct',
            'Base package': 'Base package',
            'Project name': 'Nama proyek',
            'Project group': 'Grup proyek',
            'Java version': 'Versi Java',
            'Spring Boot version': 'Versi Spring Boot',
            'Use Lombok': 'Gunakan Lombok',
            'Code style': 'Gaya kode',
            'Injection': 'Injeksi',
            'Validation': 'Validasi',
            'DTO style': 'Gaya DTO',
            'DTO shape': 'Bentuk DTO',
            'Exception': 'Exception',
            'OpenAPI': 'OpenAPI',
            'Pagination': 'Paginasi',
            'Schema-aware features': 'Fitur berbasis skema',
            'Audit fields': 'Kolom audit',
            'Soft delete': 'Soft delete',
            'Project tooling': 'Tooling proyek',
            'Emit Dockerfile + docker-compose.yml': 'Buat Dockerfile + docker-compose.yml',
            'CI pipeline': 'Pipeline CI',
            'Migrations': 'Migrasi',
            'Logging': 'Logging',
            'App config format': 'Format config aplikasi',
            'Add correlation-id filter': 'Tambah filter correlation-id',
            'Generate integration tests': 'Buat integration test',
            'Security': 'Keamanan',
            'Style': 'Gaya',
            'JWT secret': 'Secret JWT',
            'Expiration minutes': 'Menit kedaluwarsa',
            'Users': 'Pengguna',
            'Output': 'Output',
            'Mode': 'Mode',
            'Use project directory': 'Gunakan direktori proyek',
            'Directory': 'Direktori',
            'Tables': 'Tabel',
            'Class-name strip prefix': 'Prefix class yang dibuang',
            'Include globs': 'Glob include',
            'Exclude globs': 'Glob exclude',
            'Table overrides (JSON)': 'Override tabel (JSON)',
            'Revert': 'Kembalikan',
            'Save': 'Simpan',
            'Modified': 'Diubah',
            'Saved.': 'Tersimpan.',
            'Testing connection...': 'Mengetes koneksi...',
            'Refreshing tables...': 'Memuat ulang tabel...',
            'No non-junction tables found.': 'Tidak ada tabel non-junction.',
            'Attribute': 'Atribut',
            'Description': 'Deskripsi',
            'Example': 'Contoh',
            'Help': 'Bantuan',
            'Help:': 'Bantuan:',
        },
        ja: {
            'Umaboot configuration': 'Umaboot 設定',
            'Language': '言語',
            'Connection': '接続',
            'Source': 'ソース',
            'Database type': 'データベース種別',
            'Host': 'ホスト',
            'Database': 'データベース',
            'Parameters': 'パラメータ',
            'JDBC URL': 'JDBC URL',
            'Schema file': 'スキーマファイル',
            'Browse...': '参照...',
            'Username': 'ユーザー名',
            'Password': 'パスワード',
            'Schema': 'スキーマ',
            'Test Connection': '接続テスト',
            'Refresh Tables': 'テーブル更新',
            'Project': 'プロジェクト',
            'Architecture': 'アーキテクチャ',
            'Persistence': '永続化',
            'Build tool': 'ビルドツール',
            'MyBatis style': 'MyBatis スタイル',
            'Use MapStruct': 'MapStruct を使用',
            'Base package': 'ベースパッケージ',
            'Project name': 'プロジェクト名',
            'Project group': 'プロジェクトグループ',
            'Java version': 'Java バージョン',
            'Spring Boot version': 'Spring Boot バージョン',
            'Use Lombok': 'Lombok を使用',
            'Code style': 'コードスタイル',
            'Injection': 'インジェクション',
            'Validation': 'バリデーション',
            'DTO style': 'DTO スタイル',
            'DTO shape': 'DTO 形状',
            'Exception': '例外',
            'OpenAPI': 'OpenAPI',
            'Pagination': 'ページネーション',
            'Schema-aware features': 'スキーマ連携機能',
            'Audit fields': '監査フィールド',
            'Soft delete': '論理削除',
            'Project tooling': 'プロジェクトツール',
            'Emit Dockerfile + docker-compose.yml': 'Dockerfile + docker-compose.yml を生成',
            'CI pipeline': 'CI パイプライン',
            'Migrations': 'マイグレーション',
            'Logging': 'ログ',
            'App config format': 'アプリ設定形式',
            'Add correlation-id filter': 'correlation-id フィルタを追加',
            'Generate integration tests': '統合テストを生成',
            'Security': 'セキュリティ',
            'Style': 'スタイル',
            'JWT secret': 'JWT シークレット',
            'Expiration minutes': '有効期限(分)',
            'Users': 'ユーザー',
            'Output': '出力',
            'Mode': 'モード',
            'Use project directory': 'プロジェクトディレクトリを使用',
            'Directory': 'ディレクトリ',
            'Tables': 'テーブル',
            'Class-name strip prefix': 'クラス名から除去するプレフィックス',
            'Include globs': 'Include glob',
            'Exclude globs': 'Exclude glob',
            'Table overrides (JSON)': 'テーブル上書き (JSON)',
            'Revert': '元に戻す',
            'Save': '保存',
            'Modified': '変更済み',
            'Saved.': '保存しました。',
            'Testing connection...': '接続をテスト中...',
            'Refreshing tables...': 'テーブルを更新中...',
            'No non-junction tables found.': '非ジャンクションテーブルがありません。',
            'Attribute': '属性',
            'Description': '説明',
            'Example': '例',
            'Help': 'ヘルプ',
            'Help:': 'ヘルプ:',
        },
    };

    const initialState = vscode.getState && vscode.getState();
    if (uiLanguage && initialState && initialState.language) {
        uiLanguage.value = initialState.language;
    }

    function lang() {
        return uiLanguage ? uiLanguage.value : 'en';
    }

    function t(text) {
        return (TRANSLATIONS[lang()] && TRANSLATIONS[lang()][text]) || text;
    }

    function applyI18n() {
        document.documentElement.lang = lang();
        for (const el of document.querySelectorAll('h1, h2, label, button')) {
            if (el.classList.contains('help-button')) continue;
            const source = el.dataset.i18nSource || directText(el);
            if (!source) continue;
            el.dataset.i18nSource = source;
            setDirectText(el, t(source));
        }
        updateHelpButtons();
        if (status.classList.contains('dirty')) {
            status.textContent = t('Modified');
        }
    }

    function directText(el) {
        for (const node of Array.from(el.childNodes)) {
            if (node.nodeType === Node.TEXT_NODE && node.nodeValue.trim()) {
                return node.nodeValue.trim();
            }
        }
        return '';
    }

    function setDirectText(el, value) {
        for (const node of Array.from(el.childNodes)) {
            if (node.nodeType === Node.TEXT_NODE && node.nodeValue.trim()) {
                node.nodeValue = node.nodeValue.startsWith(' ') ? ` ${value}` : value;
                return;
            }
        }
        el.appendChild(document.createTextNode(value));
    }

    function updateHelpButtons() {
        for (const button of document.querySelectorAll('.help-button')) {
            const id = button.dataset.helpId;
            if (!id) continue;
            button.title = formatHelp(id);
            button.setAttribute('aria-label', `${t('Help:')} ${t(HELP[id][0])}`);
        }
    }

    function readForm() {
        const cfg = cloneConfig(lastLoaded || {});
        const gen = ensureObject(cfg, 'generation');
        const source = $('connectionSource').value;
        const dbType = normalizeDatabaseType($('databaseType').value);

        delete gen.schemaFile;
        delete gen.schemaDialect;

        if (source === 'script') {
            delete cfg.connection;
            cfg.schemaFile = $('schemaFile').value.trim();
            cfg.schemaDialect = dbType;
        } else {
            delete cfg.schemaFile;
            delete cfg.schemaDialect;
            const conn = ensureObject(cfg, 'connection');
            conn.mode = source;
            conn.type = dbType;
            conn.username = $('connectionUsername').value;
            conn.password = $('connectionPassword').value;
            conn.schema = $('connectionSchema').value.trim() || defaultSchemaFor(dbType);
            delete conn.driver;

            if (source === 'host') {
                conn.host = $('connectionHost').value.trim();
                conn.database = $('connectionDatabase').value.trim();
                conn.params = $('connectionParams').value.trim();
                delete conn.url;
            } else {
                conn.url = $('connectionUrl').value.trim();
                conn.database = parseDatabaseFromUrl(conn.url) || '';
                delete conn.host;
                delete conn.params;
            }
        }

        gen.architecture = $('architecture').value;
        gen.persistence = $('persistence').value;
        gen.buildTool = $('buildTool').value;
        gen.basePackage = $('basePackage').value.trim();
        gen.projectName = $('projectName').value.trim();
        gen.projectGroup = $('projectGroup').value.trim() || 'com.example';
        gen.springBootVersion = $('springBootVersion').value.trim() || defaultSpringBootFor($('javaVersion').value);
        gen.javaVersion = $('javaVersion').value;
        gen.useLombok = $('useLombok').checked;

        ensureObject(gen, 'openapi').style = $('openapiStyle').value;
        ensureObject(gen, 'injection').style = $('injectionStyle').value;
        ensureObject(gen, 'validation').style = $('validationStyle').value;
        const dto = ensureObject(gen, 'dto');
        dto.style = $('dtoStyle').value;
        dto.shape = $('dtoShape').value;
        ensureObject(gen, 'exception').style = $('exceptionStyle').value;
        ensureObject(gen, 'audit').enabled = $('auditEnabled').checked;
        ensureObject(gen, 'softDelete').enabled = $('softDeleteEnabled').checked;
        ensureObject(gen, 'docker').enabled = $('dockerEnabled').checked;
        ensureObject(gen, 'ci').style = $('ciStyle').value;
        const logging = ensureObject(gen, 'logging');
        logging.style = $('loggingStyle').value;
        logging.correlationId = $('loggingCorrelationId').checked;
        ensureObject(gen, 'tests').enabled = $('testsEnabled').checked;
        ensureObject(gen, 'migrations').style = $('migrationsStyle').value;
        ensureObject(gen, 'pagination').style = $('paginationStyle').value;
        ensureObject(gen, 'jpa').useMapStruct = $('useMapStruct').checked;
        ensureObject(gen, 'mybatis').style = $('mybatisStyle').value;
        ensureObject(gen, 'applicationConfig').format = $('applicationConfigFormat').value;

        gen.security = readSecurity(gen.security || {});
        gen.output = Object.assign({}, gen.output || {}, { mode: $('outputMode').value });
        if ($('useProjectDirectory').checked) {
            gen.outputDir = '.';
        } else {
            const outputDir = $('outputDir').value.trim();
            if (outputDir) gen.outputDir = outputDir;
            else delete gen.outputDir;
        }

        const tables = ensureObject(gen, 'tables');
        tables.include = splitLines($('tablesInclude').value);
        tables.exclude = splitLines($('tablesExclude').value);
        tables.classNameStripPrefix = $('classNameStripPrefix').value.trim();
        try {
            const overrides = parseTableOverridesText($('tableOverrides').value);
            if (overrides) tables.overrides = overrides;
            else delete tables.overrides;
        } catch {
            // Save validation reports the parse error. Keep the previous config
            // usable for Test Connection / Refresh Tables while the user edits.
        }

        return cfg;
    }

    function readSecurity(existing) {
        const style = $('securityStyle').value;
        const block = cloneConfig(existing || {});
        block.style = style;
        if (style !== 'none') {
            block.users = parseUserLines($('securityUsers').value);
        }
        if (style === 'jwt') {
            const previousJwt = block.jwt || {};
            block.jwt = {
                secret: $('jwtSecret').value,
                expirationMinutes: Number($('jwtExpirationMinutes').value) || 60,
                header: previousJwt.header || 'Authorization',
                prefix: previousJwt.prefix || 'Bearer ',
            };
        }
        return block;
    }

    function writeForm(cfg) {
        const root = cfg || {};
        const conn = root.connection || {};
        const gen = root.generation || {};
        const tables = gen.tables || {};
        const sec = gen.security || {};
        const jwt = sec.jwt || {};
        const schemaFile = root.schemaFile || gen.schemaFile || '';
        const source = schemaFile ? 'script' : (conn.mode === 'host' ? 'host' : 'url');
        const dbType = normalizeDatabaseType(
            root.schemaDialect || gen.schemaDialect || conn.type || conn.driver || deriveTypeFromUrl(conn.url),
        );

        $('connectionSource').value = source;
        $('databaseType').value = dbType;
        $('connectionHost').value = conn.host || '';
        $('connectionDatabase').value = conn.database || parseDatabaseFromUrl(conn.url) || '';
        $('connectionParams').value = conn.params || '';
        $('connectionUrl').value = conn.url || '';
        $('schemaFile').value = schemaFile || '';
        $('connectionUsername').value = conn.username || '';
        $('connectionPassword').value = conn.password || '';
        $('connectionSchema').value = conn.schema || defaultSchemaFor(dbType);

        $('architecture').value = gen.architecture || 'mvc';
        $('persistence').value = gen.persistence || 'jpa';
        $('buildTool').value = gen.buildTool || 'maven';
        $('mybatisStyle').value = (gen.mybatis && gen.mybatis.style) || 'xml';
        $('useMapStruct').checked = !!(gen.jpa && gen.jpa.useMapStruct);
        $('basePackage').value = gen.basePackage || '';
        $('projectName').value = gen.projectName || '';
        $('projectGroup').value = gen.projectGroup || '';
        $('javaVersion').value = String(gen.javaVersion || '17');
        $('springBootVersion').value = gen.springBootVersion || '';
        $('useLombok').checked = gen.useLombok !== false;

        $('openapiStyle').value = (gen.openapi && gen.openapi.style) || 'yaml';
        $('injectionStyle').value = (gen.injection && gen.injection.style) || 'constructor';
        $('validationStyle').value = (gen.validation && gen.validation.style) || 'jakarta';
        $('dtoStyle').value = (gen.dto && gen.dto.style) || 'class';
        $('dtoShape').value = (gen.dto && gen.dto.shape) || 'separate';
        $('exceptionStyle').value = (gen.exception && gen.exception.style) || 'problemdetail';
        $('paginationStyle').value = (gen.pagination && gen.pagination.style) || 'offset';

        $('auditEnabled').checked = (gen.audit && gen.audit.enabled) !== false;
        $('softDeleteEnabled').checked = (gen.softDelete && gen.softDelete.enabled) !== false;
        $('dockerEnabled').checked = !!(gen.docker && gen.docker.enabled);
        $('ciStyle').value = (gen.ci && gen.ci.style) || 'none';
        $('migrationsStyle').value = (gen.migrations && gen.migrations.style) || 'none';
        $('loggingStyle').value = (gen.logging && gen.logging.style) || 'plain';
        $('applicationConfigFormat').value = (gen.applicationConfig && gen.applicationConfig.format) || 'yaml';
        $('loggingCorrelationId').checked = !!(gen.logging && gen.logging.correlationId);
        $('testsEnabled').checked = !!(gen.tests && gen.tests.enabled);

        $('securityStyle').value = sec.style || 'none';
        $('securityUsers').value = formatUserLines(sec.users || []);
        $('jwtSecret').value = jwt.secret || '';
        $('jwtExpirationMinutes').value = String(jwt.expirationMinutes || 60);

        $('outputMode').value = (gen.output && gen.output.mode) || 'standalone';
        $('useProjectDirectory').checked = gen.outputDir === '.';
        $('outputDir').value = gen.outputDir === '.' ? '' : (gen.outputDir || '');

        $('classNameStripPrefix').value = tables.classNameStripPrefix || '';
        $('tablesInclude').value = (tables.include || []).join('\n');
        $('tablesExclude').value = (tables.exclude || []).join('\n');
        $('tableOverrides').value = tables.overrides
            ? JSON.stringify(tables.overrides, null, 2)
            : '';

        applyConditionalSections();
        applyCrossFieldRules();
        clearDirty();
    }

    function cloneConfig(value) {
        if (!value || typeof value !== 'object') return {};
        return JSON.parse(JSON.stringify(value));
    }

    function ensureObject(parent, key) {
        if (!parent[key] || typeof parent[key] !== 'object' || Array.isArray(parent[key])) {
            parent[key] = {};
        }
        return parent[key];
    }

    function splitLines(text) {
        return text
            .split(/\r?\n/)
            .map((s) => s.trim())
            .filter((s) => s.length > 0 && !s.startsWith('#'));
    }

    function parseUserLines(text) {
        const out = [];
        for (const line of splitLines(text)) {
            const parts = line.split(':');
            if (parts.length < 2) continue;
            const username = parts[0].trim();
            const password = parts[1].trim();
            const roles = parts[2]
                ? parts[2].split(',').map((r) => r.trim()).filter(Boolean)
                : ['USER'];
            out.push({ username, password, roles });
        }
        return out;
    }

    function formatUserLines(users) {
        return users
            .map((u) => `${u.username || ''}:${u.password || ''}:${(u.roles || []).join(',')}`)
            .join('\n');
    }

    function parseTableOverridesText(text) {
        const raw = String(text || '').trim();
        if (!raw) return undefined;
        const parsed = JSON.parse(raw);
        if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
            throw new Error('Table overrides must be a JSON object.');
        }
        return parsed;
    }

    function normalizeDatabaseType(value) {
        const v = String(value || 'postgresql').toLowerCase();
        if (v === 'postgres') return 'postgresql';
        if (['postgresql', 'mysql', 'mariadb', 'sqlserver', 'sqlite'].includes(v)) return v;
        return 'postgresql';
    }

    function deriveTypeFromUrl(url) {
        const u = String(url || '').toLowerCase();
        if (u.startsWith('jdbc:mariadb:')) return 'mariadb';
        if (u.startsWith('jdbc:mysql:')) return 'mysql';
        if (u.startsWith('jdbc:sqlserver:')) return 'sqlserver';
        if (u.startsWith('jdbc:sqlite:')) return 'sqlite';
        return 'postgresql';
    }

    function parseDatabaseFromUrl(url) {
        if (!url) return '';
        if (url.startsWith('jdbc:sqlserver:')) {
            const lower = url.toLowerCase();
            const idx = lower.indexOf('databasename=');
            if (idx < 0) return '';
            const start = idx + 'databasename='.length;
            const end = url.indexOf(';', start);
            return end < 0 ? url.substring(start) : url.substring(start, end);
        }
        if (url.startsWith('jdbc:sqlite:')) {
            return url.substring('jdbc:sqlite:'.length);
        }
        const scheme = url.indexOf('://');
        if (scheme < 0) return '';
        const pathStart = url.indexOf('/', scheme + 3);
        if (pathStart < 0) return '';
        const q = url.indexOf('?', pathStart);
        return (q < 0 ? url.substring(pathStart + 1) : url.substring(pathStart + 1, q)).replace(/\/+$/, '');
    }

    function defaultSchemaFor(dbType) {
        if (dbType === 'sqlserver') return 'dbo';
        if (dbType === 'sqlite') return 'main';
        return 'public';
    }

    function defaultSpringBootFor(java) {
        return java === '8' || java === '11' ? '2.7.18' : '3.3.5';
    }

    function applyConditionalSections() {
        const source = $('connectionSource').value;
        toggle(sourceHost, source === 'host');
        toggle(sourceUrl, source === 'url');
        toggle(sourceScript, source === 'script');
        toggle(liveConnectionFields, source !== 'script');
        $('btn-test-connection').disabled = source === 'script';

        const securityStyle = $('securityStyle').value;
        toggle(securityUsersBox, securityStyle !== 'none');
        toggle(securityJwtBox, securityStyle === 'jwt');

        const projectDir = $('useProjectDirectory').checked;
        $('outputDir').disabled = projectDir;
        if (projectDir) $('outputDir').value = '';
    }

    function toggle(el, visible) {
        if (!el) return;
        el.classList.toggle('hidden', !visible);
    }

    function applyCrossFieldRules() {
        const java = $('javaVersion').value;
        const sb = $('springBootVersion').value.trim();
        const expectedMajor = (java === '8' || java === '11') ? '2' : '3';
        if (!sb || sb[0] !== expectedMajor) {
            $('springBootVersion').value = defaultSpringBootFor(java);
        }

        const sbMajor = ($('springBootVersion').value.trim()[0] === '2') ? 2 : 3;
        setOption($('dtoStyle'), 'record', sbMajor === 3);
        if (sbMajor === 2 && $('dtoStyle').value === 'record') $('dtoStyle').value = 'class';

        setOption($('exceptionStyle'), 'problemdetail', sbMajor === 3);
        if (sbMajor === 2 && $('exceptionStyle').value === 'problemdetail') {
            $('exceptionStyle').value = 'envelope';
        }

        const cursorAvail = $('architecture').value === 'mvc' && $('persistence').value === 'jpa';
        setOption($('paginationStyle'), 'cursor', cursorAvail);
        if (!cursorAvail && $('paginationStyle').value === 'cursor') {
            $('paginationStyle').value = 'offset';
        }

        const useLombok = $('useLombok').checked;
        setOption($('injectionStyle'), 'lombok', useLombok);
        if (!useLombok && $('injectionStyle').value === 'lombok') {
            $('injectionStyle').value = 'constructor';
        }

        const scriptMode = $('connectionSource').value === 'script';
        setOption($('persistence'), 'jooq', !scriptMode);
        if (scriptMode && $('persistence').value === 'jooq') {
            $('persistence').value = 'jpa';
        }
    }

    function setOption(selectEl, value, enabled) {
        for (const opt of selectEl.options) {
            if (opt.value === value) {
                opt.disabled = !enabled;
                opt.hidden = !enabled;
            }
        }
    }

    function markDirty() {
        status.classList.add('dirty');
        status.textContent = t('Modified');
    }

    function clearDirty() {
        status.classList.remove('dirty');
        status.textContent = '';
    }

    function showMessage(text, ok) {
        connectionResult.classList.remove('ok', 'err');
        if (ok === true) connectionResult.classList.add('ok');
        if (ok === false) connectionResult.classList.add('err');
        connectionResult.textContent = text ? t(text) : '';
    }

    function guardConfig(cfg) {
        const gen = cfg.generation || {};
        if (!gen.basePackage) return 'Base package is required.';
        if (!gen.projectName) return 'Project name is required.';
        try {
            parseTableOverridesText($('tableOverrides').value);
        } catch (err) {
            return err && err.message ? err.message : 'Table overrides must be valid JSON.';
        }

        if (!cfg.connection) {
            const schemaFile = String(cfg.schemaFile || '').trim();
            if (!schemaFile) return 'Schema file is required when source is script.';
            if (gen.persistence === 'jooq') return 'jOOQ requires a live database connection, not schema-file mode.';
        } else {
            const conn = cfg.connection || {};
            if (conn.mode === 'host') {
                if (conn.params && conn.params.startsWith('?')) {
                    return "Parameters must not start with '?'.";
                }
                if (conn.type !== 'sqlite' && !conn.host) return 'Host is required when source is host.';
                if (conn.type !== 'sqlite' && !conn.database) return 'Database is required when source is host.';
                if (conn.type === 'sqlite' && !conn.database) return 'Database file path is required for SQLite.';
            } else if (!conn.url) {
                return 'JDBC URL is required when source is url.';
            }
        }

        if (gen.security && gen.security.style === 'jwt') {
            const secret = (gen.security.jwt && gen.security.jwt.secret) || '';
            if (secret.length < 32) {
                return 'JWT secret should be at least 32 characters.';
            }
        }
        return null;
    }

    function installHelpButtons() {
        const labels = Array.from(document.querySelectorAll('label[for], label[data-help-for]'));
        for (const label of labels) {
            const id = label.getAttribute('for') || label.getAttribute('data-help-for');
            if (!id || !HELP[id] || label.querySelector('.help-button')) continue;

            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'help-button';
            button.dataset.helpId = id;
            button.textContent = '?';
            button.title = formatHelp(id);
            button.setAttribute('aria-label', `${t('Help:')} ${t(HELP[id][0])}`);
            button.addEventListener('click', (event) => {
                event.preventDefault();
                event.stopPropagation();
                helpPanel.textContent = formatHelp(id);
                helpPanel.classList.remove('hidden');
            });
            label.appendChild(button);
        }
    }

    function formatHelp(id) {
        const entry = HELP[id];
        return `${t('Attribute')}: ${t(entry[0])}\n\n${t('Description')}: ${entry[1]}\n${t('Example')}: ${entry[2]}`;
    }

    if (uiLanguage) {
        uiLanguage.addEventListener('change', () => {
            if (vscode.setState) {
                vscode.setState(Object.assign({}, vscode.getState && vscode.getState(), {
                    language: uiLanguage.value,
                }));
            }
            applyI18n();
        });
    }

    form.addEventListener('input', () => {
        applyConditionalSections();
        applyCrossFieldRules();
        markDirty();
    });
    form.addEventListener('change', () => {
        applyConditionalSections();
        applyCrossFieldRules();
        markDirty();
    });

    form.addEventListener('submit', (e) => {
        e.preventDefault();
        const cfg = readForm();
        const guard = guardConfig(cfg);
        if (guard) {
            showMessage(guard, false);
            return;
        }
        showMessage('', undefined);
        vscode.postMessage({ command: 'save', config: cfg });
    });

    $('btn-revert').addEventListener('click', () => {
        if (lastLoaded) writeForm(lastLoaded);
    });

    $('btn-test-connection').addEventListener('click', () => {
        const cfg = readForm();
        const guard = guardConfig(cfg);
        if (guard) {
            showMessage(guard, false);
            return;
        }
        showMessage('Testing connection...', undefined);
        vscode.postMessage({ command: 'testConnection', config: cfg });
    });

    $('btn-refresh-tables').addEventListener('click', () => {
        const cfg = readForm();
        const guard = guardConfig(cfg);
        if (guard) {
            showMessage(guard, false);
            return;
        }
        showMessage('Refreshing tables...', undefined);
        vscode.postMessage({ command: 'refreshTables', config: cfg });
    });

    $('btn-browse-schema').addEventListener('click', () => {
        vscode.postMessage({ command: 'browseSchemaFile' });
    });

    window.addEventListener('message', (ev) => {
        const msg = ev.data;
        if (!msg || typeof msg.command !== 'string') return;
        switch (msg.command) {
            case 'load':
                lastLoaded = cloneConfig(msg.config || {});
                writeForm(lastLoaded);
                break;
            case 'saved':
                clearDirty();
                lastLoaded = readForm();
                showMessage('Saved.', true);
                setTimeout(() => showMessage('', undefined), 2000);
                break;
            case 'connectionResult':
                showMessage(msg.message || '', !!msg.ok);
                break;
            case 'tablesResult':
                if (Array.isArray(msg.tables) && msg.tables.length > 0) {
                    const includeArea = $('tablesInclude');
                    const existing = splitLines(includeArea.value);
                    const set = new Set(existing);
                    msg.tables.forEach((t) => set.add(t));
                    includeArea.value = Array.from(set).join('\n');
                    showMessage(`Imported ${msg.tables.length} tables into the include list.`, true);
                    markDirty();
                } else {
                    showMessage('No non-junction tables found.', true);
                }
                break;
            case 'schemaFileSelected':
                if (typeof msg.path === 'string') {
                    $('schemaFile').value = msg.path;
                    markDirty();
                }
                break;
            default:
                break;
        }
    });

    applyI18n();
    installHelpButtons();
    applyI18n();
    vscode.postMessage({ command: 'ready' });
})();

/*
 * Umaboot configuration webview script.
 *
 * Talks to the extension host (configEditor.ts) over postMessage. Protocol:
 *
 *   host  → webview:  { command: "load",   config: <yaml-as-object> }
 *   host  → webview:  { command: "saved" }
 *   host  → webview:  { command: "connectionResult", ok: boolean, message: string }
 *   host  → webview:  { command: "tablesResult", tables: string[] }
 *
 *   webview → host:  { command: "ready" }
 *   webview → host:  { command: "save", config: <yaml-as-object> }
 *   webview → host:  { command: "testConnection" }
 *   webview → host:  { command: "refreshTables" }
 *   webview → host:  { command: "appendInclude", value: string }
 *
 * The "config" object on the wire mirrors the umaboot.yaml schema 1:1 — i.e.
 * {connection: {...}, generation: {...}}. The host owns all YAML I/O; the
 * webview is purely a form.
 */
(function () {
    'use strict';
    /* global acquireVsCodeApi */
    const vscode = acquireVsCodeApi();

    /** Track loaded config so revert / dirty work without round-tripping. */
    let lastLoaded = null;

    // ============================================================
    // Element references
    // ============================================================
    const $ = (id) => document.getElementById(id);
    const form = $('config-form');
    const status = $('status');
    const connectionResult = $('connection-result');

    // Group conditional sections.
    const securityJwtBox = $('security-jwt');
    const securityUsersBox = $('security-users');

    // ============================================================
    // Form ⇄ object marshalling
    // ============================================================

    function readForm() {
        return {
            connection: {
                url: $('connectionUrl').value.trim(),
                username: $('connectionUsername').value,
                password: $('connectionPassword').value,
                schema: $('connectionSchema').value.trim() || 'public',
            },
            generation: {
                architecture: $('architecture').value,
                persistence: $('persistence').value,
                basePackage: $('basePackage').value.trim(),
                projectName: $('projectName').value.trim(),
                projectGroup: $('projectGroup').value.trim() || 'com.example',
                springBootVersion: $('springBootVersion').value.trim() || defaultSpringBootFor($('javaVersion').value),
                javaVersion: $('javaVersion').value,
                useLombok: $('useLombok').checked,
                openapi: { style: $('openapiStyle').value },
                injection: { style: $('injectionStyle').value },
                validation: { style: $('validationStyle').value },
                dto: { style: $('dtoStyle').value, shape: $('dtoShape').value },
                exception: { style: $('exceptionStyle').value },
                audit: { enabled: $('auditEnabled').checked },
                softDelete: { enabled: $('softDeleteEnabled').checked },
                docker: { enabled: $('dockerEnabled').checked },
                ci: { style: $('ciStyle').value },
                logging: {
                    style: $('loggingStyle').value,
                    correlationId: $('loggingCorrelationId').checked,
                },
                tests: { enabled: $('testsEnabled').checked },
                pagination: { style: $('paginationStyle').value },
                security: readSecurity(),
                output: { mode: $('outputMode').value },
                outputDir: $('outputDir').value.trim() || null,
                tables: {
                    include: splitLines($('tablesInclude').value),
                    exclude: splitLines($('tablesExclude').value),
                },
            },
        };
    }

    function readSecurity() {
        const style = $('securityStyle').value;
        const block = { style };
        if (style !== 'none') {
            block.users = parseUserLines($('securityUsers').value);
        }
        if (style === 'jwt') {
            block.jwt = {
                secret: $('jwtSecret').value,
                expirationMinutes: Number($('jwtExpirationMinutes').value) || 60,
                header: 'Authorization',
                prefix: 'Bearer ',
            };
        }
        return block;
    }

    function writeForm(cfg) {
        const conn = (cfg && cfg.connection) || {};
        const gen = (cfg && cfg.generation) || {};
        const tables = gen.tables || {};
        const sec = gen.security || {};
        const jwt = sec.jwt || {};

        $('connectionUrl').value = conn.url || '';
        $('connectionUsername').value = conn.username || '';
        $('connectionPassword').value = conn.password || '';
        $('connectionSchema').value = conn.schema || '';

        $('architecture').value = gen.architecture || 'mvc';
        $('persistence').value = gen.persistence || 'jpa';
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
        $('loggingStyle').value = (gen.logging && gen.logging.style) || 'plain';
        $('loggingCorrelationId').checked = !!(gen.logging && gen.logging.correlationId);
        $('testsEnabled').checked = !!(gen.tests && gen.tests.enabled);

        $('securityStyle').value = sec.style || 'none';
        $('securityUsers').value = formatUserLines(sec.users || []);
        $('jwtSecret').value = jwt.secret || '';
        $('jwtExpirationMinutes').value = String(jwt.expirationMinutes || 60);

        $('outputMode').value = (gen.output && gen.output.mode) || 'standalone';
        $('outputDir').value = gen.outputDir || '';

        $('tablesInclude').value = (tables.include || []).join('\n');
        $('tablesExclude').value = (tables.exclude || []).join('\n');

        applyConditionalSections();
        applyCrossFieldRules();
        clearDirty();
    }

    // ============================================================
    // Helpers
    // ============================================================

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

    function defaultSpringBootFor(java) {
        return java === '8' || java === '11' ? '2.7.18' : '3.3.5';
    }

    function applyConditionalSections() {
        const securityStyle = $('securityStyle').value;
        toggle(securityUsersBox, securityStyle !== 'none');
        toggle(securityJwtBox, securityStyle === 'jwt');
    }

    function toggle(el, visible) {
        if (!el) return;
        el.classList.toggle('hidden', !visible);
    }

    /**
     * Java/Spring Boot pairing rules + dependent dropdown gates that match the
     * Generation record's cross-validation server-side.
     */
    function applyCrossFieldRules() {
        const java = $('javaVersion').value;
        const sb = $('springBootVersion').value.trim();

        // Auto-fill the Spring Boot version on Java change if it's empty or
        // doesn't match the current Java major.
        const expectedMajor = (java === '8' || java === '11') ? '2' : '3';
        if (!sb || (sb[0] !== expectedMajor)) {
            $('springBootVersion').value = defaultSpringBootFor(java);
        }

        // SB 2 disables incompatible options.
        const sbMajor = ($('springBootVersion').value.trim()[0] === '2') ? 2 : 3;
        const dtoStyle = $('dtoStyle');
        const exceptionStyle = $('exceptionStyle');
        const paginationStyle = $('paginationStyle');

        // dto.style: record requires Java 14+ — gate to SB3 only.
        setOption(dtoStyle, 'record', sbMajor === 3);
        if (sbMajor === 2 && dtoStyle.value === 'record') dtoStyle.value = 'class';

        // exception.style: problemdetail requires Spring 6.
        setOption(exceptionStyle, 'problemdetail', sbMajor === 3);
        if (sbMajor === 2 && exceptionStyle.value === 'problemdetail') {
            exceptionStyle.value = 'envelope';
        }

        // Cursor pagination is currently MVC + JPA only.
        const arch = $('architecture').value;
        const pers = $('persistence').value;
        const cursorAvail = arch === 'mvc' && pers === 'jpa';
        setOption(paginationStyle, 'cursor', cursorAvail);
        if (!cursorAvail && paginationStyle.value === 'cursor') {
            paginationStyle.value = 'offset';
        }

        // Lombok injection requires useLombok.
        const injectionStyle = $('injectionStyle');
        const useLombok = $('useLombok').checked;
        setOption(injectionStyle, 'lombok', useLombok);
        if (!useLombok && injectionStyle.value === 'lombok') {
            injectionStyle.value = 'constructor';
        }
    }

    /** Show/hide a single <option> by value while keeping the select itself simple. */
    function setOption(selectEl, value, enabled) {
        for (const opt of selectEl.options) {
            if (opt.value === value) {
                opt.disabled = !enabled;
                opt.hidden = !enabled;
            }
        }
    }

    // ============================================================
    // Dirty tracking
    // ============================================================

    function markDirty() {
        status.classList.add('dirty');
        status.textContent = 'Modified';
    }

    function clearDirty() {
        status.classList.remove('dirty');
        status.textContent = '';
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

    // ============================================================
    // Buttons
    // ============================================================

    form.addEventListener('submit', (e) => {
        e.preventDefault();
        const cfg = readForm();
        // Cross-field guards mirroring the Java-side Generation constructor —
        // surface friendly errors before round-tripping to the host.
        const guard = guardConfig(cfg);
        if (guard) {
            connectionResult.classList.remove('ok');
            connectionResult.classList.add('err');
            connectionResult.textContent = guard;
            return;
        }
        connectionResult.textContent = '';
        connectionResult.classList.remove('ok', 'err');
        vscode.postMessage({ command: 'save', config: cfg });
    });

    $('btn-revert').addEventListener('click', () => {
        if (lastLoaded) writeForm(lastLoaded);
    });

    $('btn-test-connection').addEventListener('click', () => {
        const cfg = readForm();
        connectionResult.classList.remove('ok', 'err');
        connectionResult.textContent = 'Testing connection…';
        vscode.postMessage({ command: 'testConnection', config: cfg });
    });

    $('btn-refresh-tables').addEventListener('click', () => {
        const cfg = readForm();
        connectionResult.classList.remove('ok', 'err');
        connectionResult.textContent = 'Refreshing tables…';
        vscode.postMessage({ command: 'refreshTables', config: cfg });
    });

    function guardConfig(cfg) {
        const gen = cfg.generation;
        if (!gen.basePackage) return 'Base package is required.';
        if (!gen.projectName) return 'Project name is required.';
        if (gen.security.style === 'jwt') {
            const secret = (gen.security.jwt && gen.security.jwt.secret) || '';
            if (secret.length < 16) {
                return 'JWT secret should be at least 32 characters; externalize before deploying.';
            }
        }
        return null;
    }

    // ============================================================
    // Host messages
    // ============================================================

    window.addEventListener('message', (ev) => {
        const msg = ev.data;
        if (!msg || typeof msg.command !== 'string') return;
        switch (msg.command) {
            case 'load':
                lastLoaded = msg.config || {};
                writeForm(lastLoaded);
                break;
            case 'saved':
                clearDirty();
                lastLoaded = readForm();
                connectionResult.classList.remove('err');
                connectionResult.classList.add('ok');
                connectionResult.textContent = 'Saved.';
                setTimeout(() => {
                    connectionResult.textContent = '';
                    connectionResult.classList.remove('ok');
                }, 2000);
                break;
            case 'connectionResult':
                connectionResult.classList.remove('ok', 'err');
                connectionResult.classList.add(msg.ok ? 'ok' : 'err');
                connectionResult.textContent = msg.message || '';
                break;
            case 'tablesResult':
                connectionResult.classList.remove('err');
                connectionResult.classList.add('ok');
                if (Array.isArray(msg.tables) && msg.tables.length > 0) {
                    const includeArea = $('tablesInclude');
                    const existing = splitLines(includeArea.value);
                    const set = new Set(existing);
                    msg.tables.forEach((t) => set.add(t));
                    includeArea.value = Array.from(set).join('\n');
                    connectionResult.textContent = `Imported ${msg.tables.length} tables into the include list.`;
                    markDirty();
                } else {
                    connectionResult.textContent = 'No non-junction tables found.';
                }
                break;
            default:
                break;
        }
    });

    // ============================================================
    // Boot
    // ============================================================
    vscode.postMessage({ command: 'ready' });
})();

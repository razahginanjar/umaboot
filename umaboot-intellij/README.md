# Umaboot-intellij

IntelliJ plugin that runs the Umaboot generation pipeline directly inside the IDE — with a form-based Settings panel so you don't have to hand-edit YAML.

## What it adds to the IDE

| Surface | What you see |
|---|---|
| Plugins list / Marketplace | A 40x40 anvil + spark icon (light + dark variants) |
| Right-gutter tool window stripe | A small anvil icon labeled "Umaboot" |
| Tools menu | "Umaboot: Generate" entry with the anvil icon |
| **Settings → Tools → Umaboot** | **Form editor with Connection / Tables / Generation groups** |
| Project view, recent files, file finder | `umaboot.yaml` shows the Umaboot document icon |
| **Editor gutter on `umaboot.yaml`** | **Green play icon — click to run Generate without leaving the file** |
| Notifications | A balloon reporting `[mvc/jpa, overlay (auto)]` etc. on success |
| Action enablement | The Tools menu action is grayed out when the project has no `umaboot.yaml` |

## Settings panel walkthrough

`Ctrl+Alt+S` → **Tools → Umaboot**.

### 1. Connection
The Connection group has been redesigned in v0.8 around two equivalent input modes:

- **Database type** — dropdown: `postgresql` or `mysql`. Drives the JDBC URL prefix and tells the introspector which engine to use. More engines slot in here later.
- **Connection mode** — radio toggle that swaps the field card:
  - **Host mode** (default): you provide `Host:` (e.g. `localhost:5432`), `Database:` (the database name — Postgres calls it the catalog, MySQL the database), and optional `Parameters:` (extra JDBC params, **no leading `?`** — the program adds it automatically). The program composes the JDBC URL as `jdbc:<type>://<host>/<database>[?<params>]`. Trying to type `?useSSL=false` in Parameters surfaces a clear error.
  - **URL mode**: a single **JDBC URL** field. Paste a full URL verbatim. The host / database / params fields are hidden — the URL is the only source of connection info, and the database is parsed out of the URL path. Useful for unusual hosts, SSH tunnels, or pasting from a colleague's connection screen.
- **Schema** — Postgres schema (e.g. `public`), shared across modes. MySQL leaves it empty by default but is permitted to fill it (legacy "I-typed-the-DB-name-here" workaround still works as a fallback).
- **Username / Password / Test Connection** — same as before. **Test Connection** is now lenient: it works even when `Database:` is empty, so you can verify host + credentials before deciding on a database.

The first save of an existing project rewrites a pre-v0.8 `umaboot.yaml` (flat `url:` + `schema:` shape) into the new mode-aware shape. The legacy file still loads — no manual editing required.

### 2. Tables to generate
- Click **Refresh Tables** — the plugin reads the live schema using the introspector that matches your URL (Postgres or MySQL), filters out pure junction tables, and shows everything else as checkboxes.
- The state of each checkbox controls whether the table goes into `generation.tables.include`. Unchecked tables are simply absent from the include list.
- The list survives Refresh: if you've checked / unchecked tables manually, those choices are preserved across re-introspections.

### 3. Generation
- Architecture (mvc / hexagonal / ddd), Persistence (jpa / mybatis / jooq), MyBatis style (xml / annotation), Use MapStruct
- Base package, project name + group
- **Spring Boot version** (live-fetched dropdown — see below)
- **Java version** (curated LTS dropdown: 17 / 21)
- Use Lombok
- **OpenAPI style** (yaml / annotation / none)
- Output mode (standalone / overlay), **App config format** (`yaml` / `properties` — generated `application.yml` vs `application.properties`), **Use project directory** checkbox (one-click `outputDir: .` shortcut — generated files land alongside `umaboot.yaml`), Output dir

Click **Apply** — the panel writes back to `<projectRoot>/umaboot.yaml`. Comments are not preserved on round-trip; if you want commented configs, hand-edit the file.

### 4. Version dropdowns

Both the **Spring Boot version** and **Java version** combos are editable and populated asynchronously when the panel opens. Three independent online sources, each with its own 24-hour disk cache at `~/.umaboot/cache/versions.json`:

- **Spring Boot 3.x** ← Spring Initializr metadata (`start.spring.io/metadata/client`). Filters out pre-releases (`-M`, `-RC`, `SNAPSHOT`) and `x.x.x` pointers; sorts descending.
- **Spring Boot 2.x** ← Maven Central search (`search.maven.org/solrsearch/select?q=...v:2.7.*`). Initializr stopped listing 2.x after its EOL, so we query Maven Central directly.
- **Java majors** ← Foojay DiscoAPI (`api.foojay.io/disco/v3.0/major_versions?ga=true&maintained=true&term_of_support=lts`) — the same source IntelliJ's Foojay JDK resolver uses, which keeps the list current as new LTS releases land.

The two combos filter each other bidirectionally: pick Java 8 and the Spring Boot combo narrows to 2.7.x; pick Spring Boot 3.3.5 and the Java combo narrows to 17/21.

If you're offline (or behind a proxy that blocks any of the three calls), the panel falls back to the most recent on-disk cache for that source, then to a hardcoded curated list. You can always type a custom version directly into either combo — it doesn't have to be one of the suggested values.

## Generating

Two ways:
- **Tools menu → Umaboot: Generate**
- **Click the green play icon** in the gutter of `umaboot.yaml`

Both run the same pipeline: introspect → relationship engine → table filter → architecture generator → render. A balloon notification reports the file count, output directory, and `[architecture/persistence, mode]` used.

If the project has a `pom.xml` at its root and you didn't explicitly set `output.mode`, the plugin auto-overlays so generated files land directly in your existing `src/main/java/...` instead of a separate `generated/` folder. The notification labels this with `(auto)`.

## Build & install

```bash
# 1. Publish umaboot-core to the local Maven repo:
cd ..
mvn install -pl umaboot-core -am -DskipTests

# 2. Build the plugin:
cd umaboot-intellij
gradle :buildPlugin       # produces build/distributions/umaboot-intellij-*.zip
```

Install the resulting zip via **Settings → Plugins → ⚙ → Install Plugin from Disk**.

For development:
```bash
gradle :runIde            # launches a sandbox IDE with the plugin live-loaded
```

## Architecture

```
Settings → Tools → Umaboot (UmabootSettingsConfigurable)
    ↓
UmabootSettingsPanel  ─────────────────►  UmabootYamlIO.save()
    │                                              │
    ├─ Test Connection (pooled thread)             ▼
    └─ Refresh Tables → Introspector ──►   umaboot.yaml at project root
                                                   ▲
                                                   │
                                                   │ load
GenerateAction (Tools menu / gutter icon)         ─┘
    └─► UmabootRunner
            └─► GenerationPipeline.run(config)
                    └─► writes generated files + VFS.refresh()
```

## Icon assets

| Asset | Path | Used by |
|---|---|---|
| Plugin (icon)     | `META-INF/pluginIcon.svg`        | Plugins list, Marketplace |
| Action / toolbar  | `icons/umaboot.png`              | `UmabootIcons.ACTION` |
| Tool window       | `icons/umaboot_toolwindow.png`   | `UmabootIcons.TOOL_WINDOW` |
| File type         | `icons/umaboot_file.png`         | `UmabootIcons.FILE` |

All loaded through `IconLoader` so the IDE applies theming and HiDPI scaling automatically.

## Status (v0.7+)

- Connection: **redesigned in v0.8** — type dropdown + Host/URL mode toggle + separate Database/Schema/Parameters fields. Backwards-compatible with pre-v0.8 yamls (auto-migrated on first save).
- Settings panel: complete (Connection, Tables, Generation)
- Test Connection: complete (lenient — works without a database picked)
- Refresh Tables: complete (Postgres + MySQL)
- Tools menu action + gutter run icon: complete
- DDD options (aggregate-roots, belongsTo): config is preserved on save but no UI yet — edit YAML for those
- Diff preview before apply: not yet (planned)

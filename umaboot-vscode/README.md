# umaboot-vscode

Visual Studio Code extension for **Umaboot** — a Spring Boot CRUD generator. Provides an Activity Bar dashboard, CodeLens above `umaboot.yaml`, file decoration badge, status bar, live database introspection, and a webview-based form panel that mirrors the IntelliJ plugin's Settings dialog.

## What it adds to VS Code

| Surface | What you see |
|---|---|
| **Activity Bar** | Umaboot icon in the left strip — click to open the Dashboard. |
| **Dashboard** | Three sections: **Configuration**, **Tables**, **Actions**. Inline buttons: Test Connection (Configuration), Refresh Tables (Tables). Title bar buttons: Edit Configuration (form), Refresh, Open YAML. |
| **Form panel** | Click the pencil icon (or run `Umaboot: Edit Configuration`) to open a webview form mirroring the IntelliJ Settings dialog. ~25 fields grouped into Connection / Project / Code style / Schema-aware / Tooling / Security / Output / Tables sections. Theme-aware (light / dark / high-contrast). Test Connection and Refresh Tables work inline against the form's current values, no save needed first. |
| **Welcome view** | If no `umaboot.yaml` exists, the dashboard offers Create or Edit. |
| **CodeLens** | `▶ Generate ⇄ Diff ✓ Apply 🔌 Test Connection` above the first line of `umaboot.yaml`. |
| **File decoration** | Small `U` badge with theme-aware accent on `umaboot.yaml` in the Explorer / open editors. |
| **Status bar** | `🚀 <projectName>` on the left. Click to open the dashboard. |
| **Output channel** | Dedicated "Umaboot" channel with CLI stdout / stderr in real time. |
| **File watcher** | Dashboard, CodeLens, badge, and status bar refresh when the YAML changes on disk. |
| **Command palette** | `Umaboot: Generate / Diff / Apply / Test Connection / Refresh Tables / Edit Configuration / Open umaboot.yaml / Refresh Dashboard / Create umaboot.yaml`. |

## Form panel — how it works

The form is a `WebviewPanel` rendered from `media/configEditor.html` + `.css` + `.js`. Communication with the extension host is via `postMessage`:

| Direction | Message | Used for |
|---|---|---|
| host → webview | `{command: "load", config}` | Initial population on open / after save |
| host → webview | `{command: "saved"}` | Save acknowledged — clears dirty marker |
| host → webview | `{command: "connectionResult", ok, message}` | Test Connection result |
| host → webview | `{command: "tablesResult", tables}` | Refresh Tables — webview merges into the include list |
| webview → host | `{command: "ready"}` | Webview booted; host responds with `load` |
| webview → host | `{command: "save", config}` | User clicked Save → host writes YAML |
| webview → host | `{command: "testConnection", config}` | Spawn `umaboot test-connection` against a temp YAML built from form |
| webview → host | `{command: "refreshTables", config}` | Spawn `umaboot list-tables` against a temp YAML |

Test Connection / Refresh Tables write the form's *current* values to a temp file under `globalStorageUri/editor-<ts>.yaml` and run the CLI against that — so you can iterate on the form without saving first.

The form mirrors the IntelliJ panel's cross-field gates client-side (e.g. `dto.style: record` is hidden on Spring Boot 2.x; `injection.style: lombok` is hidden when Use Lombok is unchecked; cursor pagination is gated to MVC + JPA). Server-side validation in `UmabootConfig.Generation` is the source of truth — the form gates are just for friendlier UX.

**Comments are not preserved** when the form saves. Same limitation as the IntelliJ plugin's `UmabootYamlIO` — js-yaml drops them on round-trip. A banner is added at the top reminding users to hand-edit if they want commented configs.

## CLI subcommands the extension calls

Added in Phase R.2 to support live introspection from the editor:

| Subcommand | Purpose | Exit codes |
|---|---|---|
| `umaboot test-connection --config umaboot.yaml` | JDBC ping; prints `OK <product> <version>` | `0` ok / `1` connection failed / `2` bad config |
| `umaboot list-tables --config umaboot.yaml [--all]` | Non-junction table names, one per line | `0` ok / `1` introspection failed / `2` bad config |

## Build & package

```bash
cd umaboot-vscode
npm install
npm run compile          # produces ./out/extension.js
npx @vscode/vsce package # produces umaboot-0.5.0.vsix
```

## Use

1. Build the CLI fat JAR from the parent project: `mvn -pl umaboot-cli -am package` (produces `umaboot-cli/target/umaboot.jar`).
2. Either put the `umaboot` script on `PATH` or set `umaboot.cliPath` to `java -jar /path/to/umaboot.jar` in VS Code settings.
3. Open a project with a `umaboot.yaml` (or click the Activity Bar entry → "Edit configuration with form" to seed and edit one).
4. Use the form panel to set everything up, hit Save, then run Generate from the dashboard, the command palette, or the YAML's CodeLens.

## Settings

| Key | Default | Purpose |
|---|---|---|
| `umaboot.cliPath` | `umaboot` | Path / command to invoke the CLI |
| `umaboot.configFile` | `umaboot.yaml` | Workspace-relative path to the config file |

## Status

| Phase | Scope | Status |
|---|---|---|
| **R.1** | Activity Bar + tree dashboard + casing fixes | ✅ shipped |
| **R.2** | CodeLens, status bar, file decoration, `test-connection` + `list-tables` CLI subcommands, tree-view inline actions | ✅ shipped |
| **R.3** | Webview-based form panel — IntelliJ Settings parity | ✅ shipped |

Phase R closes out feature parity with the IntelliJ plugin. Future work would be polish: marketplace publishing, more detailed table picker (checkboxes per table), DDD aggregate-roots editor with drag/drop.

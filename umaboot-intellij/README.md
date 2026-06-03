# Umaboot IntelliJ Plugin

IntelliJ plugin for Umaboot. It provides a form-based configuration editor,
table introspection, direct generation, and a visual preview/merge workflow for
generated file changes.

## What It Adds

| Surface | What you see |
|---|---|
| Settings | `Settings -> Tools -> Umaboot` form editor |
| Tool window | Umaboot panel with Apply, Generate, Preview / Merge, and Open Settings |
| Tools menu | `Umaboot: Generate` and `Umaboot: Preview / Merge` |
| Editor gutter | Run icon on `umaboot.yaml` |
| Project view | Umaboot icon for `umaboot.yaml` |
| Notifications | Generation and preview status messages |

## Settings Panel

Open `Settings -> Tools -> Umaboot`.

The form supports:

- Database source: Host, URL, or Script
- Database type: PostgreSQL, MySQL, MariaDB, SQL Server, SQLite
- Table refresh and table include selection
- Per-table class-name and column Java-type overrides
- Architecture, persistence, build tool, Java/Spring Boot versions
- MyBatis style, MapStruct, OpenAPI, validation, DTO, exception style
- Docker, CI, Flyway migrations, tests, logging, security
- Output mode, application config format, output directory
- UI language: English, Bahasa Indonesia, Japanese

Click `Apply` to write the current form values to `umaboot.yaml`.

## Generate

Use one of:

- `Tools -> Umaboot: Generate`
- Tool window `Generate`
- Gutter run icon on `umaboot.yaml`

Generate runs the full pipeline and writes generated files immediately.

If the project root has a `pom.xml` and the config did not explicitly choose an
output mode, the runner uses overlay mode automatically so generated source goes
into the existing project instead of a separate `generated/` directory.

## Preview / Merge

Use one of:

- `Tools -> Umaboot: Preview / Merge`
- Tool window `Preview / Merge`

Preview / Merge runs the same generation preparation but does not write files
immediately. It compares generated output against the target output directory.

For every new or modified generated file, IntelliJ opens its built-in merge
viewer:

- left/current side: existing file content, or empty content for a new file
- base: current content
- right/generated side: newly generated content

Accepting the merge writes that single file. Cancelling leaves that file
unchanged. This is most useful for overlay mode, where generated source paths can
collide with files in an existing project.

## Build And Install

Publish core locally first:

```bash
mvn install -pl umaboot-core -am -DskipTests
```

Build the plugin:

```bash
cd umaboot-intellij
gradle :buildPlugin
```

Install the zip from `build/distributions/` using:

```text
Settings -> Plugins -> gear icon -> Install Plugin from Disk
```

For development:

```bash
gradle :runIde
```

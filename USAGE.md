# Umaboot Usage Guide

This guide covers installation, configuration, and normal usage for generating
Spring Boot projects with Umaboot.

## Installation

### Requirements

To build and run Umaboot itself:

- JDK 17+
- Maven 3.9+
- Gradle, only for building the IntelliJ plugin
- Node.js and npm, only for building the VS Code extension
- Docker, only if you use generated Testcontainers tests or the sample database fixture

Generated projects can target Java 8, 11, 17, or 21 depending on the selected
Spring Boot version.

### Install the CLI

Build from source:

```bash
git clone <repo-url>
cd generate-code-spring-plugin
mvn clean install -DskipTests
```

Run the CLI directly:

```bash
java -jar umaboot-cli/target/umaboot.jar --help
```

Optional wrapper for Linux/macOS:

```sh
#!/bin/sh
exec java -jar /path/to/umaboot.jar "$@"
```

Optional wrapper for Windows PowerShell:

```powershell
java -jar C:\path\to\umaboot.jar @args
```

Put the wrapper on `PATH` if you want to run `umaboot generate` instead of
`java -jar ... generate`.

### Install the IntelliJ Plugin

Build the plugin:

```bash
mvn install -pl umaboot-core -am -DskipTests
cd umaboot-intellij
gradle :buildPlugin
```

Install the generated zip from:

```text
umaboot-intellij/build/distributions/
```

In IntelliJ, open `Settings -> Plugins -> gear icon -> Install Plugin from Disk`
and select the zip.

### Install the VS Code Extension

Build and package the extension:

```bash
cd umaboot-vscode
npm install
npm run compile
npx @vscode/vsce package
```

Install the generated `.vsix` through VS Code:

```text
Extensions view -> ... menu -> Install from VSIX
```

The VS Code extension shells out to the CLI. If `umaboot` is not on `PATH`, set:

```json
{
  "umaboot.cliPath": "java -jar C:/path/to/umaboot.jar"
}
```

## Quick Start

### 1. Create `umaboot.yaml`

For a live PostgreSQL database:

```yaml
connection:
  mode: host
  type: postgresql
  host: localhost:5432
  database: app
  schema: public
  username: app
  password: app

generation:
  architecture: mvc
  persistence: jpa
  buildTool: maven
  basePackage: com.example.app
  projectName: app-api
  springBootVersion: 3.3.5
  javaVersion: "17"
  output:
    mode: standalone
```

For schema-file generation:

```yaml
schemaFile: ./schema.sql
schemaDialect: postgresql

generation:
  architecture: mvc
  persistence: jpa
  buildTool: gradle
  basePackage: com.example.app
  projectName: app-api
  springBootVersion: 3.3.5
  javaVersion: "17"
```

Use either `connection` or `schemaFile`, not both.

### 2. Validate the Schema Source

Live database:

```bash
java -jar umaboot-cli/target/umaboot.jar test-connection --config umaboot.yaml
java -jar umaboot-cli/target/umaboot.jar list-tables --config umaboot.yaml
```

Schema-file mode:

```bash
java -jar umaboot-cli/target/umaboot.jar list-tables --config umaboot.yaml
```

`test-connection` intentionally skips schema-file mode because no live database
connection is configured.

### 3. Generate

```bash
java -jar umaboot-cli/target/umaboot.jar generate --config umaboot.yaml
```

By default, standalone output is written to `./generated`. Run the generated app:

```bash
cd generated
mvn spring-boot:run
```

For Gradle output:

```bash
cd generated
gradle bootRun
```

## Configuration

### Schema Source

Host mode composes the JDBC URL from parts:

```yaml
connection:
  mode: host
  type: mysql
  host: localhost:3306
  database: inventory
  params: useSSL=false
  username: root
  password: root
```

URL mode accepts a raw JDBC URL:

```yaml
connection:
  mode: url
  type: sqlserver
  url: jdbc:sqlserver://localhost:1433;databaseName=inventory;encrypt=false
  schema: dbo
  username: sa
  password: password
```

Script mode parses a checked-in SQL DDL file:

```yaml
schemaFile: ./src/main/resources/db/migration/V1__init_schema.sql
schemaDialect: mariadb
```

Supported `type` and `schemaDialect` values:

- `postgresql`
- `mysql`
- `mariadb`
- `sqlserver`
- `sqlite`

### Generation Options

Common options:

```yaml
generation:
  architecture: mvc          # mvc | hexagonal | ddd
  persistence: jpa           # jpa | mybatis | jooq
  buildTool: maven           # maven | gradle
  basePackage: com.example.app
  projectName: app-api
  projectGroup: com.example
  springBootVersion: 3.3.5
  javaVersion: "17"
  useLombok: true
```

Persistence options:

```yaml
generation:
  jpa:
    useMapStruct: false
  mybatis:
    style: xml               # xml | annotation
```

API and code style:

```yaml
generation:
  openapi:
    style: annotation        # yaml | annotation | none
  injection:
    style: constructor       # constructor | lombok | autowired
  validation:
    style: jakarta           # jakarta | none | service
  dto:
    style: class             # class | record
    shape: separate          # separate | single
  exception:
    style: problemdetail     # problemdetail | envelope
```

Tooling:

```yaml
generation:
  docker:
    enabled: true
  ci:
    style: github            # none | github | gitlab
  migrations:
    style: flyway            # none | flyway
  tests:
    enabled: true
  applicationConfig:
    format: yaml             # yaml | properties
```

When `migrations.style: flyway` is enabled, Umaboot emits Flyway dependencies
and an initial migration. In schema-file mode, the migration comes from the SQL
file. In live-DB mode, Umaboot renders a best-effort initial DDL from the
introspected model.

Table selection and naming:

```yaml
generation:
  tables:
    include: [customer*, order*]
    exclude: [audit_*, flyway_schema_history]
    classNameStripPrefix: app_
```

Per-table overrides:

```yaml
generation:
  tables:
    overrides:
      app_users:
        className: Account
        columns:
          metadata:
            javaType: java.util.Map<String,Object>
```

## Architecture Choices

### MVC

Use `architecture: mvc` for the fastest path to a runnable CRUD API. Generated
files include controllers, services, repositories, DTOs, mappers, entities, and
common application classes.

### Hexagonal

Use `architecture: hexagonal` when you want domain models and ports separated
from inbound web adapters and outbound persistence adapters.

### DDD

Use `architecture: ddd` when you want aggregate roots, commands, domain events,
application services, and infrastructure persistence split by aggregate.

Example:

```yaml
generation:
  architecture: ddd
  ddd:
    aggregateRoots: [customers, orders]
    nonRoots: [order_lines]
    belongsTo:
      order_lines: orders
```

## Persistence Choices

| Persistence | When to use it |
|---|---|
| `jpa` | Default choice for conventional CRUD and Hibernate-backed projects |
| `mybatis` | Good when SQL should stay explicit, reviewable, or DBA-owned |
| `jooq` | Good for type-safe SQL and database-first teams |

`jooq` requires a live database connection. The generated project runs jOOQ
codegen during its build, so `schemaFile` mode is rejected for `persistence:
jooq`.

## Build Tool

Maven output:

```yaml
generation:
  buildTool: maven
```

Gradle output:

```yaml
generation:
  buildTool: gradle
```

Gradle generation emits `build.gradle.kts` and `settings.gradle.kts`. If you want
a checked-in Gradle wrapper, run this once inside the generated project:

```bash
gradle wrapper
```

## Output Modes

Standalone mode creates a full project:

```yaml
generation:
  output:
    mode: standalone
  outputDir: ./generated
```

Overlay mode writes generated source files into an existing project:

```yaml
generation:
  output:
    mode: overlay
  outputDir: .
```

Overlay mode is intended for projects that already have their own build file,
application class, and application config.

## Re-running Safely

Use `diff` to inspect pending changes:

```bash
java -jar umaboot-cli/target/umaboot.jar diff --config umaboot.yaml --unified
```

Use `apply` to write changes while preserving protected regions:

```bash
java -jar umaboot-cli/target/umaboot.jar apply --config umaboot.yaml
```

Protected region example:

```java
// <umaboot:protected name="customMethods">
public void notifyCustomer(Customer customer) {
    // handwritten code
}
// </umaboot:protected>
```

The current `apply` command is safest for MVC output. For Hexagonal or DDD,
generate to a separate directory or review the diff carefully before applying.

## IntelliJ Usage

After installing the plugin:

1. Open `Settings -> Tools -> Umaboot`.
2. Choose the schema source: Host, URL, or Script.
3. Select database type, architecture, persistence, build tool, Java/Spring
   versions, output mode, and optional features.
4. Click `Refresh Tables` to load table names.
5. Optionally double-click a table to customize class or column Java types.
6. Click `Apply` to write `umaboot.yaml`.
7. Run `Umaboot: Generate` from the Tools menu, tool window, or gutter icon.

The IntelliJ UI currently has the richest table-customization support.

## VS Code Usage

After installing the extension:

1. Open a folder containing `umaboot.yaml`, or use the dashboard to create one.
2. Configure `umaboot.cliPath` if the CLI is not on `PATH`.
3. Open the Umaboot Activity Bar dashboard.
4. Use `Edit Configuration` to edit the form.
5. Run Generate, Diff, Apply, Test Connection, or Refresh Tables from the
   dashboard, command palette, or YAML CodeLens.

The VS Code form preserves existing table overrides but does not yet provide the
full IntelliJ-style per-table override dialog.

## CLI Reference

```bash
umaboot generate --config umaboot.yaml
umaboot diff --config umaboot.yaml --unified
umaboot apply --config umaboot.yaml --dry-run
umaboot test-connection --config umaboot.yaml
umaboot list-tables --config umaboot.yaml --all
```

Use `--templates <dir>` with `generate`, `diff`, or `apply` to override bundled
FreeMarker templates.

## Troubleshooting

`Configuration error: Schema source missing`

Set exactly one of `connection` or `schemaFile`.

`persistence: jooq requires a live connection`

Switch to `connection` mode or use `jpa`/`mybatis` for schema-file generation.

Generated integration tests fail in CI

Enable Flyway migrations or provide equivalent schema setup so the test database
has the required tables. Generated Testcontainers tests need Docker.

Gradle project has no wrapper

Run `gradle wrapper` once in the generated project if you want wrapper scripts
checked in.

Maven or Gradle cannot compile jOOQ repositories

Run the generated project build once with live database access so jOOQ codegen
can create `${basePackage}.jooq.Tables`.

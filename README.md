# Umaboot

Umaboot generates Spring Boot CRUD projects from a relational database schema.
It can read the schema from a live JDBC connection or from a checked-in SQL DDL
file, then render a complete project using the architecture, persistence stack,
build tool, and supporting features selected in `umaboot.yaml`.

For day-to-day installation and usage, start with [USAGE.md](USAGE.md).

## What It Generates

Umaboot can generate:

- Spring Boot 2.7.x or 3.x projects
- Java 8, 11, 17, or 21 compatible source, based on the selected Spring Boot line
- MVC, Hexagonal, or DDD architecture
- JPA, MyBatis, or jOOQ persistence
- Maven or Gradle Kotlin DSL builds
- REST controllers, services, repositories/adapters, DTOs, mappers, exceptions, and application config
- Optional OpenAPI, Docker, CI, Flyway migrations, integration tests, security, logging, pagination, audit, and soft-delete support

Supported database targets are PostgreSQL, MySQL, MariaDB, SQL Server, and
SQLite. SQL-file mode supports the same dialect set for code generation.

## Modules

| Module | Build | Purpose |
|---|---|---|
| `umaboot-core` | Maven | Schema introspection, config model, relationship analysis, generators, templates, diff/merge logic |
| `umaboot-cli` | Maven | Picocli CLI: `generate`, `diff`, `apply`, `test-connection`, `list-tables` |
| `umaboot-test-fixtures` | Maven | Sample schemas and Docker Compose fixtures for tests |
| `umaboot-intellij` | Gradle | IntelliJ plugin with settings UI, tool window, gutter action, and direct generation |
| `umaboot-vscode` | npm + TypeScript | VS Code extension that shells out to the CLI |

## Build From Source

Build the Maven modules:

```bash
mvn clean install -DskipTests
```

The CLI fat JAR is created at:

```text
umaboot-cli/target/umaboot.jar
```

Build the IntelliJ plugin:

```bash
mvn install -pl umaboot-core -am -DskipTests
cd umaboot-intellij
gradle :buildPlugin
```

The plugin zip is created under `umaboot-intellij/build/distributions/`.

Build the VS Code extension:

```bash
cd umaboot-vscode
npm install
npm run compile
npx @vscode/vsce package
```

## Schema Sources

Use exactly one schema source.

Live database mode:

```yaml
connection:
  mode: host
  type: postgresql
  host: localhost:5432
  database: app
  schema: public
  username: app
  password: app
```

SQL-file mode:

```yaml
schemaFile: ./schema.sql
schemaDialect: postgresql
```

`schemaFile` mode is useful for CI-friendly generation and migration-first
projects. `persistence: jooq` requires a live `connection`, because the generated
jOOQ codegen plugin needs JDBC access during the generated project build.

## Minimal Config

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

## CLI

```bash
java -jar umaboot-cli/target/umaboot.jar generate --config umaboot.yaml
java -jar umaboot-cli/target/umaboot.jar diff --config umaboot.yaml --unified
java -jar umaboot-cli/target/umaboot.jar apply --config umaboot.yaml
java -jar umaboot-cli/target/umaboot.jar test-connection --config umaboot.yaml
java -jar umaboot-cli/target/umaboot.jar list-tables --config umaboot.yaml
```

`apply` preserves protected regions in generated Java files. The current command
is safest for MVC output; for Hexagonal and DDD, prefer regenerating into a clean
directory or review the diff carefully before applying changes.

## IDE Plugins

The IntelliJ plugin is the most complete UI today. It supports live DB mode,
script mode, database type selection, Gradle/Maven, language selection, table
customization, version filtering, and direct generation.

The VS Code extension provides a dashboard, CodeLens, status bar integration,
and a form editor aligned with the current config model. It invokes the CLI for
generation and introspection, so configure `umaboot.cliPath` if the `umaboot`
command is not on your `PATH`.

## More Documentation

- [USAGE.md](USAGE.md): installation, configuration, and usage guide
- [umaboot-intellij/README.md](umaboot-intellij/README.md): IntelliJ plugin details
- [umaboot-vscode/README.md](umaboot-vscode/README.md): VS Code extension details

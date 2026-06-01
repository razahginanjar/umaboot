# Umaboot — v0.7

Java tool that introspects a PostgreSQL, MySQL, MariaDB, SQL Server, or SQLite schema (live database via JDBC, or a checked-in `.sql` DDL file) and generates a complete Spring Boot CRUD project. Three architectures (MVC, Hexagonal, DDD), three persistence backends (JPA, MyBatis xml/annotation, jOOQ), MapStruct, OpenAPI emission (yaml or springdoc annotations), table include/exclude filters, overlay mode that drops into existing projects, and an IntelliJ plugin with a form-based settings panel.

> **Status:** v0.7. MVC + JPA + Postgres path is fully end-to-end and integration-tested. Hexagonal and DDD have dedicated generators with separate domain models, ports/adapters, aggregate roots, commands, and domain events. MyBatis (XML + annotation), jOOQ (MVC), MapStruct, OpenAPI (yaml + annotation), MySQL, diff/apply commands, overlay mode, and version dropdowns in the IDE plugin all work. 59 unit tests pass.

## Modules

| Module                    | Build      | Purpose                                                                                          |
|---------------------------|------------|--------------------------------------------------------------------------------------------------|
| `umaboot-parent`          | Maven      | Root POM — dependency management                                                                 |
| `umaboot-core`            | Maven      | Pure-Java library: introspection, schema model, relationship engine, table filter, generators (MVC/Hexagonal/DDD), templates, diff/merge engines, YAML config, version metadata fetcher |
| `umaboot-cli`             | Maven      | Picocli entry point: `generate`, `diff`, `apply`. Produces a fat JAR                              |
| `umaboot-test-fixtures`   | Maven      | Sample SQL + Docker Compose for integration testing                                              |
| `umaboot-intellij`        | Gradle     | IntelliJ plugin: settings panel with live-fetched version dropdowns, tool window, gutter run icon, file-icon provider |
| `umaboot-vscode`          | npm + tsc  | VS Code extension skeleton (TypeScript) — shells out to the CLI                                  |

## Build

```bash
mvn clean install -Dtest='!*IntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false
```

The CLI fat JAR lands at `umaboot-cli/target/umaboot.jar`.

To build the IntelliJ plugin:

```bash
mvn install -pl umaboot-core -am -DskipTests   # publish core to ~/.m2
cd umaboot-intellij
gradle :clean :buildPlugin                     # produces build/distributions/umaboot-intellij-*.zip
```

Then **Settings → Plugins → ⚙ → Install Plugin from Disk** in IntelliJ and pick the zip.

## Schema source

Two ways to feed Umaboot a schema:

```yaml
# Option A: live database introspection via JDBC (the v0.x default)
connection:
  mode: host
  type: postgresql            # postgresql | mysql | mariadb | sqlserver | sqlite
  host: localhost:5432
  database: app
  schema: public
  username: app
  password: app

# Option B: parse a checked-in .sql file with JSqlParser — no live DB needed.
# Mutually exclusive with `connection:`. Set exactly one.
schemaFile: ./schema.sql
```

**When to use which:**

- **`connection:`** — your schema lives in a running database. Best for established projects, rich introspection (Postgres ENUMs from `pg_type`, MySQL ENUMs from `INFORMATION_SCHEMA.COLUMNS`, table/column comments, FKs).
- **`schemaFile:`** — your schema lives as DDL in version control. Best for new projects, CI-friendly codegen (no Docker / no DB), reproducible output across machines, and pairs nicely with Flyway/Liquibase migrations where the same `V1__init.sql` becomes both the codegen source AND the runtime migration.

**Supported in v1 of `schemaFile:` mode:** Postgres + MySQL + MariaDB + SQL Server + SQLite DDL. `CREATE TABLE` (with PK, FK, UNIQUE, NOT NULL, DEFAULT, AUTO_INCREMENT/SERIAL/IDENTITY/`IDENTITY(1,1)` / SQLite `INTEGER PRIMARY KEY` rowid alias), `ALTER TABLE … ADD CONSTRAINT … FOREIGN KEY`, Postgres `CREATE TYPE … AS ENUM`, MySQL inline `ENUM('a','b')`, `COMMENT ON TABLE/COLUMN`, MySQL inline `COMMENT 'text'`. Triggers, functions, views, partitions, generated columns, CHECK enforcement, and `CREATE INDEX` are silently skipped — they don't affect codegen.

**Restrictions:** `persistence: jooq` requires `connection:` mode (the generated `jooq-codegen-maven` plugin needs a live JDBC connection at `mvn compile` time — config-load rejects `schemaFile + jooq` with a clear error). SQLite + JPA additionally pulls `hibernate-community-dialects` since Hibernate has no built-in SQLite dialect.

## Configuration highlights (full example: `umaboot.example.yaml`)

```yaml
generation:
  architecture: mvc | hexagonal | ddd
  persistence:  jpa | mybatis | jooq           # all 3 architectures support all 3 backends
  buildTool:    maven | gradle                 # default: maven. gradle = Kotlin DSL build.gradle.kts

  jpa:
    useMapStruct: false                        # JPA-only opt-in
  mybatis:
    style: xml | annotation                    # MyBatis-only

  openapi:
    style: yaml | annotation | none            # default: yaml. annotation = springdoc + @Tag/@Operation

  injection:
    style: constructor | lombok | autowired    # default: constructor. lombok requires useLombok: true

  validation:
    style: jakarta | none | service            # default: jakarta. service = move validation into the service layer

  dto:
    style: class | record                      # default: class. record = Java records (immutable)
    shape: separate | single                   # default: separate. (single — one DTO per entity — coming soon)

  exception:
    style: problemdetail | envelope            # default: problemdetail. envelope = stable {code,message,path,timestamp} ApiError class

  audit:                                       # auto-detect created_at/updated_at/created_by/updated_by columns
    enabled: true                              # default: true. Emits Auditable @MappedSuperclass + @EnableJpaAuditing (JPA only)

  softDelete:                                  # auto-detect deleted_at / is_deleted columns
    enabled: true                              # default: true. Emits @SQLDelete + @Where on the entity (JPA only)

  docker:
    enabled: false                             # default: false. Emits Dockerfile + docker-compose.yml
    baseImage: eclipse-temurin:17-jre-alpine
    port: 8080

  ci:
    style: none | github | gitlab              # default: none. github = .github/workflows/ci.yml; gitlab = .gitlab-ci.yml

  logging:
    style: plain | json                        # default: plain. json = logstash-logback-encoder + JSON logs
    correlationId: false                       # default: false. true = X-Correlation-Id servlet filter -> MDC

  tests:
    enabled: false                             # default: false. true = emit @SpringBootTest + Testcontainers smoke tests per entity

  pagination:
    style: offset | cursor                     # default: offset. cursor = base64-cursor over the simple PK (MVC + JPA only)

  security:
    style: none | basic | jwt                  # default: none. basic = HTTP Basic. jwt = bearer tokens via /api/auth/login
    users:                                     # in-memory only (DB-backed user lookup not yet implemented)
      - username: admin
        password: admin
        roles: [ADMIN, USER]
    jwt:
      secret: change-me-32-chars-minimum       # required when style: jwt; externalize via SPRING_SECURITY_JWT_SECRET
      expirationMinutes: 60

  output:
    mode: standalone | overlay                 # standalone = full project at ./generated
                                               # overlay    = drop sources into existing project at .

  # Glob include/exclude. Empty include = "all". Junctions auto-pruned when endpoints are filtered.
  tables:
    include: ["customer*", "order*"]
    exclude: ["audit_*", "flyway_schema_history"]

  ddd:
    aggregateRoots: []                         # empty = every non-junction table
    nonRoots:       []
    belongsTo:                                 # non-root → parent aggregate's table
      # order_lines: orders
    sharedKernelPackage: shared
```

## Commands

```bash
java -jar umaboot-cli/target/umaboot.jar generate --config umaboot.yaml
java -jar umaboot-cli/target/umaboot.jar diff     --config umaboot.yaml [--unified]
java -jar umaboot-cli/target/umaboot.jar apply    --config umaboot.yaml [--dry-run]
```

Exit codes: `generate` 0 success / 2 error; `diff` 0 no changes / 1 changes detected / 2 error; `apply` 0 clean / 1 conflicts / 2 error.

## What v0.5–v0.7 added

- **Overlay mode** (v0.6) — generated source files drop into your existing Spring Boot project root (next to your existing `pom.xml`, `Application.java`, `application.yml`). Skips project-wide files so they don't clobber yours.
- **Application config merger** (v0.6.1) — in overlay mode, appends required entries (e.g. MyBatis `mapper-locations`) to your existing `application.yml`/`.yaml`/`.properties`. Idempotent via marker comments.
- **`ResponseEntity<T>` everywhere** (v0.7) — controllers wrap responses in `ResponseEntity` for explicit status/header control. POST → 201, GET → 200, DELETE → 204.
- **Custom `PageResponse<T>`** (v0.7) — emitted under `${basePackage}.common.PageResponse`. Stable JSON shape on the wire instead of leaking Spring Data internals. Two factories: `of(Page<T>)` for JPA, `of(List<T>, page, size, total)` for Hexagonal/DDD.
- **OpenAPI style toggle** (v0.7) — `openapi.style: yaml | annotation | none`. The annotation mode emits `OpenApiConfig.java` plus `@Tag`/`@Operation` annotations on every controller, and adds `springdoc-openapi-starter-webmvc-ui` to the pom — Spring Boot then serves `/swagger-ui.html` + `/v3/api-docs` automatically.
- **IntelliJ Settings panel** (v0.7) — full form under *Settings → Tools → Umaboot* with **Test Connection** + **Refresh Tables** (live introspection → table-picker checkboxes) + **Apply** (writes to `umaboot.yaml`). Spring Boot version dropdown is live-fetched from `start.spring.io/metadata/client` (24h disk cache + offline fallback to a curated list). Java version dropdown shows the supported LTS set (17, 21).
- **Bean injection style** (v0.7) — `injection.style: constructor | lombok | autowired`. Controls how services and controllers receive their dependencies. `constructor` (default) emits an explicit constructor with `private final` fields — the modern Spring best practice. `lombok` adds `@RequiredArgsConstructor` (requires `useLombok: true`). `autowired` uses `@Autowired` field injection for teams with legacy codebases.
- **Validation style** (v0.8) — `validation.style: jakarta | none | service`. `jakarta` (default) emits `@NotBlank`/`@Size`/`@NotNull` on Request DTOs and `@Valid` on controllers. `none` strips all validation annotations. `service` keeps DTOs annotation-free and expects validation to live in the service layer.
- **DTO style** (v0.8) — `dto.style: class | record`. `class` (default) is the traditional class with getters/setters (or Lombok `@Data`). `record` emits Java records — immutable, no boilerplate, automatic `equals`/`hashCode`/`toString`. Works with both Jakarta validation and Lombok-disabled mode.
- **Exception envelope** (v0.8) — `exception.style: problemdetail | envelope`. `problemdetail` (default) uses Spring 6's RFC 7807 `ProblemDetail`. `envelope` emits a stable `ApiError` record with `{code, message, path, timestamp}` for frontends that prefer a uniform shape.
- **Audit field auto-detection** (v0.9) — Umaboot inspects each table for columns matching `audit.createdAt` / `updatedAt` / `createdBy` / `updatedBy` (defaults: `created_at` etc.). When detected on JPA entities, a generated `Auditable` `@MappedSuperclass` is emitted under `${basePackage}.common`, the entity extends it, audit columns are removed from per-entity field lists, and `@EnableJpaAuditing` is added to the application class. When `created_by`/`updated_by` are present, an `AuditorAwareConfig` stub returning `"system"` is emitted alongside.
- **Soft-delete auto-detection** (v0.9) — when a table has a `deleted_at` (timestamp) or `is_deleted` (boolean) column, the JPA entity gets `@SQLDelete` (so `repository.delete()` performs an UPDATE) and `@Where` (so reads automatically exclude soft-deleted rows). Configurable via `softDelete.column` to override the auto-detection. JPA only in v0.9; MyBatis support is on the roadmap.
- **Docker scaffolding** (v1.0) — `docker.enabled: true` emits a multi-stage `Dockerfile` and a `docker-compose.yml` with the app + a matching database (postgres or mysql, inferred from your JDBC driver). Connection details are externalized as `${DB_NAME}`/`${DB_USER}`/`${DB_PASSWORD}` env vars so passwords don't leak into version control.
- **CI scaffolding** (v1.0) — `ci.style: github` emits `.github/workflows/ci.yml`; `ci.style: gitlab` emits `.gitlab-ci.yml`. Both run `mvn package` + `mvn test` against the configured Java version.
- **JSON logging + correlation IDs** (v1.0) — `logging.style: json` swaps in a `logback-spring.xml` that writes JSON via `logstash-logback-encoder` (added to the generated pom). `logging.correlationId: true` emits a Spring servlet filter that reads the `X-Correlation-Id` request header (or generates a fresh UUID) and puts it into the SLF4J MDC for the lifetime of the request, ready for log correlation in observability tools.
- **Integration test scaffolding** (v1.1) — `tests.enabled: true` emits a shared `AbstractIntegrationTest` base class with a Testcontainers-backed Postgres or MySQL (matched to your driver), plus one `<Entity>IntegrationTest` per non-junction table containing smoke-test cases (`contextLoads`, `list_returnsOk`, `getById_unknown_returns404`). Adds `org.testcontainers:junit-jupiter` and the matching jdbc-driver Testcontainers module to the generated pom in test scope. Requires Docker to run.
- **Cursor pagination** (v1.2) — `pagination.style: cursor` switches list endpoints to keyset/cursor pagination over the entity's simple primary key. Emits a `CursorPage<T>` common class, adds a derived-query method to the JPA repository, base64-encodes/decodes the cursor in the service, and updates the controller signature to accept `?cursor=...&limit=...`. **MVC + JPA only** in v1.2 — config-load fails fast for incompatible architecture/persistence combinations. Per-table fallback to offset for tables with composite primary keys.
- **Java 8/11/17/21 foundation** (v1.3) — config + UI plumbing for picking older Java/Spring Boot stacks. `Generation` cross-validates Java vs Spring Boot major (Java 8/11 ⇒ SB 2.7; Java 17+ ⇒ SB 3.x). `VersionMetadataService.getSpringBootVersionsFor(javaVersion)` returns the matching version line. The IntelliJ Settings panel filters dropdowns bidirectionally so picking Java 8 narrows the Spring Boot combo to the curated 2.7.x line and vice versa. Generation for Spring Boot 2.x throws a clear "pending Phase L" error today; Java 17+ generation is unchanged.
- **Live version fetching** (v1.3.1) — three independent live sources, each with its own 24-hour disk cache and curated fallback. Java majors fetched from **Foojay DiscoAPI** (`api.foojay.io/disco/v3.0/major_versions?ga=true&maintained=true&term_of_support=lts`) — the same source IntelliJ's Foojay JDK resolver uses. Spring Boot 2.x fetched from **Maven Central search** (`search.maven.org/solrsearch/select?q=...v:2.7.*`) since Initializr no longer surfaces 2.x post-EOL. Spring Boot 3.x continues to come from Initializr. Cache schema updated to a multi-section `~/.umaboot/cache/versions.json` so each source refreshes independently. Pre-K.1 single-section caches are auto-treated as "no cache" and replaced on next run.
- **Spring Boot 2.7 / Java 8/11 generation** (v1.4) — full template support for the Spring Boot 2.7 stack on **MVC + JPA only**. Strategy: unified template files with inline conditionals rather than a forked `sb2/` tree, so future features land once. Templates use `${eeNamespace}` (`jakarta` for SB3, `javax` for SB2) for `persistence`/`validation`/`servlet` package imports. `ApiError` and `CursorPage` switch between record (SB3) and class (SB2) forms via `<#if springBoot3>...<#else>...</#if>`. The `pom.xml` template branches on `springBoot2` for the springdoc artifact (`springdoc-openapi-ui:1.7.0` vs `springdoc-openapi-starter-webmvc-ui:2.6.0`) and parent version. Hex / DDD architectures, MyBatis / jOOQ persistence, `dto.style: record`, `exception.style: problemdetail`, and cursor pagination are all rejected at config-load when SB2 is selected — with clear error messages. The IntelliJ Settings panel narrows the DTO and Exception combos automatically when SB2 is picked.
- **Spring Boot 2.7 for Hexagonal + DDD** (v1.5) — extends SB2 support to all three architectures (still JPA only). DDD's `Create*Command`/`Update*Command` classes and `*CreatedEvent`/`*UpdatedEvent` classes now branch on `springBoot3` between Java records (SB3) and traditional classes with constructors + accessors (SB2). The DDD `ApplicationService` template branches command-field accessor calls between record-style (`command.fieldName()`) and getter-style (`command.getFieldName()`). Hex + DDD `pom.xml` templates pick up the same SB2 springdoc branching as MVC. Hex's `ApplicationService` already used getter-style on the domain model, so no template changes there.
- **Spring Boot 2.7 for MyBatis + jOOQ** (v1.6) — completes the SB2 matrix: any architecture (MVC / Hex / DDD) × any persistence (JPA / MyBatis / jOOQ) now works on Spring Boot 2.7 / Java 8/11. The MyBatis (`org.apache.ibatis.annotations.*`) and jOOQ (`org.jooq.*`) templates were already version-agnostic; the only pom changes were branching `mybatis-spring-boot-starter:2.3.2` (SB2) vs `3.0.4` (SB3) and `jooq:3.16.23` (SB2 — last JDK 8-compatible OSS release) vs `3.19.15` (SB3). With this phase the SB2 / SB3 cells of the architecture × persistence × Java matrix are all green except for the architectural restrictions (cursor pagination, records DTOs, ProblemDetail exceptions remain SB3 only).
- **Security scaffolding** (v1.7) — `security.style: none | basic | jwt`. `none` (default) leaves the project open. `basic` adds `spring-boot-starter-security` and an in-memory `UserDetailsService` seeded from `security.users` in `umaboot.yaml`; all `/api/**` requires HTTP Basic auth. `jwt` additionally generates `SecurityConfig`, `JwtTokenService` (HS256, configurable expiry), `JwtAuthenticationFilter`, an `AuthController` exposing `POST /api/auth/login`, and `LoginRequest`/`LoginResponse` DTOs. JWT mode requires `security.jwt.secret` to be set at config load (≥32 characters recommended) — the generated `application.yml` uses `${SPRING_SECURITY_JWT_SECRET:...}` with a TODO comment urging externalization. `SecurityConfig` is branched for SB2 (`WebSecurityConfigurerAdapter`) vs SB3 (`SecurityFilterChain` bean). Database-backed user lookup, OAuth2 resource server, refresh tokens, and `/register` are out of scope — users swap in their own `UserDetailsService` when ready.

## Java × Spring Boot compatibility

| Java | Spring Boot 2.7.x          | Spring Boot 3.x | Notes |
|---|---|---|---|
| 8  | ✅ all archs / all backends | ❌ | Spring Boot 3 requires Java 17 |
| 11 | ✅ all archs / all backends | ❌ | |
| 17 | ✅ all archs / all backends | ✅ default | |
| 21 | ⚠️ unofficial              | ✅ default | |

**Phase L + M + N status:** Spring Boot 2.7 generation works for **all three architectures (MVC, Hexagonal, DDD)** on **all three persistence backends (JPA, MyBatis, jOOQ)**. The pom template auto-picks `mybatis-spring-boot-starter:2.3.2` / `jooq:3.16.23` (last JDK 8-compatible OSS line) for SB2 vs `3.0.4` / `3.19.15` for SB3. Cursor pagination, records DTOs (`dto.style: record`), and `ProblemDetail` exceptions remain Spring Boot 3 only — picking those with SB2 fails at config load with a clear message.

The plugin's Settings panel narrows combos automatically: pick `Java 8` and the Spring Boot combo shows the curated 2.7.x line; pick `Spring Boot 3.3.5` and the Java combo narrows to `17 / 21`. When SB2 is selected, `DTO style` is locked to `class` and `Exception style` is locked to `envelope`. Both version combos remain editable so a non-listed version can still be typed in directly.

## Architecture × persistence matrix

| Architecture | Generator                | JPA               | MyBatis (xml)        | MyBatis (annotation) | jOOQ                         |
|--------------|--------------------------|-------------------|----------------------|----------------------|------------------------------|
| MVC          | `MvcGenerator`           | ✅ end-to-end      | ✅                   | ✅                   | ✅ (codegen plugin in pom)    |
| Hexagonal    | `HexagonalGenerator`     | ✅ dedicated       | ✅ dedicated          | ✅ dedicated          | ✅ adapter via DSLContext     |
| DDD          | `DddGenerator`           | ✅ dedicated       | ✅ dedicated          | ✅ dedicated          | ✅ repo via DSLContext        |

## Protected regions — keep your edits across regenerations

```java
public class CustomerServiceImpl implements CustomerService {
    // ... generated code ...

    // <umaboot:protected name="customMethods">
    public void notifyCustomer(Customer c) { /* preserved on regen */ }
    // </umaboot:protected>
}
```

`umaboot apply` substitutes those blocks from the existing file into the freshly-generated file. JavaParser validates the merged Java; if not parseable, the file is overwritten and a conflict is reported (exit 1).

## Integration test

Requires Docker Desktop / Engine running locally:

```bash
mvn -pl umaboot-core test -Dtest=*IntegrationTest
```

Spins up `postgres:15-alpine`, applies the sample fixture (ENUM, comments, 1:1, 1:N, M:N junction, self-reference), runs the full pipeline, and asserts the expected files are produced.

## See also

- **`USAGE.md`** — end-user guide with quick-start, examples per architecture, OpenAPI yaml-vs-annotation tradeoffs, overlay mode walkthrough, IntelliJ plugin walkthrough, FAQ
- **`umaboot.example.yaml`** — annotated configuration template
- **`umaboot-intellij/README.md`** — plugin build + install instructions

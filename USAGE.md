# Umaboot — User Guide

This guide is for developers who want to **use** Umaboot to generate Spring Boot CRUD projects from an existing database. For internal architecture and contribution docs, see `README.md`.

## Table of contents

- [What Umaboot does](#what-umaboot-does)
- [Quick start (5 minutes)](#quick-start-5-minutes)
- [Configuration reference](#configuration-reference)
- [Examples by architecture](#examples-by-architecture)
- [Choosing persistence: JPA vs MyBatis vs jOOQ](#choosing-persistence-jpa-vs-mybatis-vs-jooq)
- [Output modes: standalone vs overlay](#output-modes-standalone-vs-overlay)
- [Re-running the generator safely](#re-running-the-generator-safely)
- [Using the IntelliJ plugin](#using-the-intellij-plugin)
- [FAQ](#faq)

## What Umaboot does

You point Umaboot at a PostgreSQL or MySQL database and a config file. It:

1. Reads your schema (tables, columns, primary keys, foreign keys, unique constraints, ENUM types, comments).
2. Detects relationships (1:1, 1:N, M:N via junction tables, self-references).
3. Generates a runnable Spring Boot 3.x project with REST endpoints, service layer, persistence layer, validation, exception handling, and an OpenAPI spec.

You pick:

- **Architecture**: `mvc` | `hexagonal` | `ddd`
- **Persistence**: `jpa` | `mybatis` | `jooq` (mybatis sub-options: `xml` or `annotation`)
- **Which tables**: include/exclude globs

The output is a normal Maven project — open it in your IDE, edit it, deploy it. Umaboot does not maintain ownership of your code.

## Quick start (5 minutes)

### 1. Install the CLI

Build it from source:

```bash
git clone <this-repo>
cd generate-code-spring-plugin
mvn clean install -DskipTests
```

The CLI fat-JAR is at `umaboot-cli/target/umaboot.jar`. You can run it directly with `java -jar`, or wrap it in a shell script:

```bash
# Linux/macOS: put this in ~/.local/bin/umaboot and chmod +x
#!/bin/sh
exec java -jar /path/to/umaboot.jar "$@"
```

```powershell
# Windows: put this in C:\Tools\umaboot.ps1
java -jar C:\path\to\umaboot.jar @args
```

### 2. Make sure your database is reachable

Umaboot connects via JDBC. If you don't have a database yet, the bundled Docker Compose file gives you a Postgres pre-loaded with a sample schema:

```bash
docker compose -f umaboot-test-fixtures/src/main/resources/docker-compose.yml up -d postgres
```

This starts Postgres on `localhost:5432` with the `umaboot` database and the sample schema (`customers`, `orders`, `products`, `tags`, plus a `product_tags` junction table).

### 3. Create `umaboot.yaml`

Copy the example and edit:

```bash
cp umaboot.example.yaml umaboot.yaml
```

Minimal config:

```yaml
connection:
  url: jdbc:postgresql://localhost:5432/umaboot
  username: postgres
  password: postgres
  schema: public

generation:
  architecture: mvc
  persistence: jpa
  basePackage: com.example.shop
  projectName: shop-api
  outputDir: ./generated
```

### 4. Run

```bash
java -jar umaboot-cli/target/umaboot.jar generate --config umaboot.yaml
```

You should see something like:

```
INFO  Loaded config from umaboot.yaml
INFO  Introspected 7 tables
Generated 56 files in /home/you/project/generated [architecture=mvc, persistence=jpa, openapi=on]
```

### 5. Run the generated project

```bash
cd generated
mvn spring-boot:run
```

The API serves at `http://localhost:8080`. Try it:

```bash
curl http://localhost:8080/api/customers
curl -X POST http://localhost:8080/api/customers \
     -H 'Content-Type: application/json' \
     -d '{"email":"alice@example.com","fullName":"Alice"}'
```

The OpenAPI spec is at `src/main/resources/openapi.yaml`. To get an interactive Swagger UI instead, set `generation.openapi.style: annotation` — the generator will add `springdoc-openapi-starter-webmvc-ui`, emit an `OpenApiConfig.java`, and decorate every controller with `@Tag` / `@Operation` so Spring Boot serves `/swagger-ui.html` and `/v3/api-docs` automatically. Pick `none` to skip OpenAPI entirely.

## Configuration reference

Full annotated example: see `umaboot.example.yaml`. The high-value settings:

| Setting | Values | What it does |
|---|---|---|
| `connection.url` | JDBC URL | The database to introspect |
| `connection.schema` | string | Schema name (Postgres) or database name (MySQL) |
| `generation.architecture` | `mvc \| hexagonal \| ddd` | Project shape |
| `generation.persistence` | `jpa \| mybatis \| jooq` | Persistence backend |
| `generation.basePackage` | Java package | Root package of generated code |
| `generation.projectName` | string | Maven `artifactId` of the generated project |
| `generation.useLombok` | `true \| false` | Toggle Lombok annotations |
| `generation.openapi.style` | `yaml \| annotation \| none` | OpenAPI emission strategy. `yaml` writes `openapi.yaml`; `annotation` adds springdoc + `@Tag`/`@Operation` so `/swagger-ui.html` is served; `none` skips all OpenAPI |
| `generation.injection.style` | `constructor \| lombok \| autowired` | Bean injection style for services/controllers. `constructor` = explicit constructor + `private final` (default, modern Spring). `lombok` = `@RequiredArgsConstructor` (requires `useLombok: true`). `autowired` = `@Autowired` field injection |
| `generation.validation.style` | `jakarta \| none \| service` | Validation strategy. `jakarta` (default) = Jakarta Bean Validation annotations + `@Valid` on controllers. `none` strips all annotations. `service` = no `@Valid`; expect validation in the service layer |
| `generation.dto.style` | `class \| record` | `class` (default) = classes with getters/setters or Lombok `@Data`. `record` = Java records (immutable, no boilerplate) |
| `generation.dto.shape` | `separate \| single` | `separate` (default) = distinct Request and Response DTOs. `single` = one DTO per entity (planned; currently behaves as `separate`) |
| `generation.exception.style` | `problemdetail \| envelope` | `problemdetail` (default) = Spring 6 `ProblemDetail` (RFC 7807). `envelope` emits an `ApiError` class with `{code, message, path, timestamp}` |
| `generation.audit.enabled` | `true \| false` | When true (default), auto-detect `created_at`/`updated_at`/`created_by`/`updated_by` columns and emit Auditable @MappedSuperclass + `@EnableJpaAuditing`. JPA only |
| `generation.audit.createdAt` / `updatedAt` / `createdBy` / `updatedBy` | column name | Override default column name conventions |
| `generation.softDelete.enabled` | `true \| false` | When true (default), auto-detect `deleted_at`/`is_deleted` columns and add `@SQLDelete` + `@Where` to the JPA entity |
| `generation.softDelete.column` | column name | Explicit column name (overrides auto-detection) |
| `generation.docker.enabled` | `true \| false` | Emit `Dockerfile` + `docker-compose.yml`. Default: false |
| `generation.docker.baseImage` | image | Runtime image. Default: `eclipse-temurin:17-jre-alpine` |
| `generation.docker.port` | int | App port. Default: 8080 |
| `generation.ci.style` | `none \| github \| gitlab` | CI pipeline scaffolding |
| `generation.logging.style` | `plain \| json` | `json` adds `logstash-logback-encoder` + a `logback-spring.xml` that emits JSON log lines |
| `generation.logging.correlationId` | `true \| false` | Emit `CorrelationIdFilter` that reads/sets `X-Correlation-Id` and pushes it to MDC |
| `generation.tests.enabled` | `true \| false` | Emit `@SpringBootTest` + Testcontainers smoke tests per entity. Requires Docker at test time |
| `generation.pagination.style` | `offset \| cursor` | `offset` (default) = `Pageable` + `PageResponse`. `cursor` = base64 cursor over the simple PK (MVC + JPA only). Tables with composite PK fall back to offset |
| `generation.security.style` | `none \| basic \| jwt` | `none` (default) = no security. `basic` = HTTP Basic + in-memory users. `jwt` = bearer tokens via `POST /api/auth/login` |
| `generation.security.users` | list of `{username, password, roles}` | In-memory user list. Required when style != none |
| `generation.security.jwt.secret` | string ≥32 chars | Required when style: jwt. Externalize via `SPRING_SECURITY_JWT_SECRET` env var before deploying |
| `generation.security.jwt.expirationMinutes` | int | Token lifetime. Default: 60 |
| `generation.output.mode` | `standalone \| overlay` | `standalone` writes a fresh project under `outputDir`; `overlay` drops sources into your existing project root |
| `generation.outputDir` | path | Where files are written; relative paths resolve from the config file |
| `generation.tables.include` | `[globs]` | Whitelist (empty = "all") |
| `generation.tables.exclude` | `[globs]` | Blacklist |
| `generation.jpa.useMapStruct` | `true \| false` | Use compile-time MapStruct mappers |
| `generation.mybatis.style` | `xml \| annotation` | Where the SQL lives |
| `generation.ddd.aggregateRoots` | `[table-names]` | Explicit aggregate roots (empty = "all non-junction") |
| `generation.ddd.nonRoots` | `[table-names]` | Tables explicitly excluded from being roots |

## Examples by architecture

### MVC (default — fastest path to a runnable API)

```yaml
generation:
  architecture: mvc
  persistence: jpa
  basePackage: com.example.shop
  projectName: shop-api
```

Per non-junction table you get:
- `entity/{Entity}.java` — JPA `@Entity`
- `repository/{Entity}Repository.java` — `JpaRepository`
- `service/{Entity}Service.java` + `service/impl/{Entity}ServiceImpl.java`
- `controller/{Entity}Controller.java` — REST endpoints with pagination
- `dto/{Entity}RequestDTO.java`, `dto/{Entity}ResponseDTO.java`
- `mapper/{Entity}DtoMapper.java`
- `exception/{Entity}NotFoundException.java`

Plus project-wide `Application.java`, `GlobalExceptionHandler.java` (RFC 7807 ProblemDetail), `application.yml`, `pom.xml`.

### Hexagonal (Ports & Adapters)

```yaml
generation:
  architecture: hexagonal
  persistence: jpa  # or: mybatis
  basePackage: com.example.shop
  projectName: shop-api
```

Per non-junction table you get:
- `domain/model/{Entity}.java` — pure POJO, no JPA, no Spring
- `domain/port/{Entity}Repository.java` — port interface (no Spring types)
- `domain/exception/{Entity}NotFoundException.java`
- `application/usecase/{Entity}UseCase.java` — inbound port
- `application/service/{Entity}ApplicationService.java` — `@Transactional`
- `adapter/in/web/{Entity}Controller.java`, `dto/`, `mapper/{Entity}WebMapper.java`
- `adapter/out/persistence/...` — JPA-flavored or MyBatis-flavored

The persistence adapter implements the domain port. Swapping JPA → MyBatis only changes files in `adapter/out/persistence/` — domain and application layers are untouched.

### DDD (aggregates + commands + domain events)

```yaml
generation:
  architecture: ddd
  persistence: jpa  # or: mybatis
  basePackage: com.example.shop
  projectName: shop-api
  ddd:
    aggregateRoots: [customers, orders]   # optional; empty = all non-junction
```

Per aggregate root you get:
- `domain/{aggregate}/{Aggregate}.java` — aggregate with `create()` factory, `updateFrom()` domain method, `pullDomainEvents()`
- `domain/{aggregate}/event/{Aggregate}CreatedEvent.java`, `{Aggregate}UpdatedEvent.java`
- `domain/{aggregate}/{Aggregate}Repository.java` — domain port
- `application/{aggregate}/{Aggregate}ApplicationService.java` — drains aggregate events, dispatches via `ApplicationEventPublisher`
- `application/{aggregate}/command/Create{Aggregate}Command.java`, `Update{Aggregate}Command.java`
- `interfaces/rest/{Aggregate}Controller.java` — receives requests, builds Commands, calls service
- `infrastructure/persistence/...` — JPA or MyBatis variant

To listen to domain events:

```java
@Component
public class CustomerListener {
    @EventListener
    public void on(CustomerCreatedEvent event) {
        System.out.println("New customer: " + event.aggregateId());
    }
}
```

## Choosing persistence: JPA vs MyBatis vs jOOQ

| | When to pick it |
|---|---|
| **JPA** (default) | You want auto-generated CRUD, `@OneToMany`/`@ManyToMany` magic, and don't mind Hibernate. Best for typical line-of-business apps. |
| **MyBatis** | You write or maintain hand-tuned SQL. The XML mapper lets your DBA / SQL author edit queries without touching Java. The annotation mode keeps SQL and Java side-by-side. |
| **jOOQ** | Type-safe SQL DSL. Ideal for complex reporting queries and database-first teams. Umaboot generates the Repository facade and wires the `jooq-codegen-maven` plugin in your generated `pom.xml`; running `mvn compile` populates `${basePackage}.jooq.Tables` with type-safe table references that the Repository imports statically. **MVC only** as of v0.7 — Hexagonal and DDD jOOQ variants are on the roadmap. |

### MyBatis: XML vs annotation

```yaml
generation:
  persistence: mybatis
  mybatis:
    style: xml          # SQL in src/main/resources/mapper/*.xml
    # or
    style: annotation   # SQL inline as @Select / @Insert / @Update / @Delete
```

- **xml** — easier to grep for queries; multi-line SQL stays readable; works well with SQL-savvy reviewers.
- **annotation** — fewer files; nice for small projects; refactoring tools touch the annotations along with the method signature.

You can switch styles by editing the config and re-running `apply` — only the mapper files change.

### jOOQ workflow

```yaml
generation:
  architecture: mvc      # MVC only as of v0.7
  persistence: jooq
```

The generated project's `pom.xml` includes `jooq-codegen-maven`, which connects to your database during the Maven build and generates type-safe table references under `${basePackage}.jooq.*`. The Repository facade imports these statically:

```java
import static com.example.shop.jooq.Tables.CUSTOMERS;

public Optional<Customer> findById(Long id) {
    return dsl.selectFrom(CUSTOMERS)
              .where(CUSTOMERS.ID.eq(id))
              .fetchOptionalInto(Customer.class);
}
```

After running `Umaboot generate`, build the project once with `mvn compile` to produce the `Tables.*` classes — without that step, the Repository won't compile. The codegen connection details default to the same DB used for introspection; edit `pom.xml`'s `<jdbc>` block if you want it to read from a different DB or environment variable.

## Output modes: standalone vs overlay

Umaboot can either produce a brand-new project or drop generated code into a project you already have. Pick the mode in `umaboot.yaml`:

```yaml
generation:
  output:
    mode: standalone   # default — creates a complete runnable project
    # or
    mode: overlay      # only emits per-table source files; skips pom.xml,
                       # Application.java, application.yml, and GlobalExceptionHandler
```

**Standalone** (default for the CLI):
- `outputDir` defaults to `./generated`
- Includes `pom.xml`, `Application.java`, `application.yml`, `GlobalExceptionHandler.java`
- Use case: greenfield project from a schema; the output folder is a complete Spring Boot project ready to `mvn spring-boot:run`

> **Tip — in-place workflow.** Set `outputDir: .` (or tick **Use project directory** in the IntelliJ Settings panel) to land the full generated project in the same directory as `umaboot.yaml`. Drop the yaml into an empty folder, edit the connection block, run `umaboot generate` — and the folder *becomes* the Spring Boot project. No `cd generated/` step before opening the IDE.

**Overlay**:
- `outputDir` defaults to `.` (current directory / project root)
- Skips `pom.xml`, `Application.java`, `application.yml`, `GlobalExceptionHandler.java`
- Per-table source files (`entity/`, `controller/`, etc.) land directly under `src/main/java/<your.basePackage>/...` of your existing project
- Use case: you already have a Spring Boot project with its own build files and want Umaboot to add CRUD endpoints to it

```
your-existing-project/                ← outputDir = . (overlay mode)
├── pom.xml                           ← YOUR existing pom (untouched)
├── src/main/java/com/yourcompany/
│   ├── Application.java              ← YOUR Spring Boot main (untouched)
│   ├── entity/                       ← generated by Umaboot
│   ├── repository/                   ← generated by Umaboot
│   ├── controller/                   ← generated by Umaboot
│   └── ...
└── src/main/resources/
    └── application.yml               ← YOUR config (untouched)
```

**The IntelliJ plugin is smarter**: if you don't explicitly set `output.mode` and your project root has a `pom.xml`, it auto-switches to overlay so you don't end up with a `generated/` subfolder cluttering your project. The success notification reports `[mvc/jpa, overlay (auto)]` so you know which mode was used.

## Re-running the generator safely

You will eventually edit generated code. Umaboot's `apply` command can re-run generation **without losing your edits** if you wrap them in protected regions:

```java
public class CustomerServiceImpl implements CustomerService {
    // ... generated code ...

    // <umaboot:protected name="customMethods">
    public void notifyCustomer(Customer c) {
        // your handwritten code stays here across regenerations
    }
    // </umaboot:protected>
}
```

Workflow:

```bash
# 1. See what will change (no writes):
java -jar umaboot.jar diff --config umaboot.yaml --unified

# 2. Apply, preserving any protected blocks you've added:
java -jar umaboot.jar apply --config umaboot.yaml
```

`apply` exits `1` if any merge produced syntactically invalid Java; in that case the freshly-generated content wins and you'll see "X conflicts" in the output.

Tip: keep `apply --dry-run` in your CI to detect schema drift early.

## Using the IntelliJ plugin

1. From the parent project, publish the core JAR locally:
   ```bash
   mvn install -pl umaboot-core -am -DskipTests
   ```
2. Build the plugin:
   ```bash
   cd umaboot-intellij
   gradle :buildPlugin
   ```
3. Install the resulting `build/distributions/umaboot-intellij-*.zip` via **IntelliJ → Settings → Plugins → ⚙ → Install Plugin from Disk**.
4. Open a project (with or without an existing `umaboot.yaml`).
5. Either:
   - Open **Settings → Tools → Umaboot** and fill in the form. Click **Apply** to write `umaboot.yaml`, then click **Generate** in the tool window OR run **Tools → Umaboot: Generate**.
   - Or run **Tools → Umaboot: Generate** directly if you already have a `umaboot.yaml`.

### Settings panel walkthrough

The form (also embedded in the right-gutter Umaboot tool window) has three groups:

- **Connection** — JDBC URL, username, password, schema. Click **Test Connection** to verify reachability before doing anything else; success shows the database product name + version in green.
- **Tables to generate** — Click **Refresh Tables** to introspect the live schema. The plugin populates a checkbox list with every non-junction table; the boxes you tick drive `tables.include` in the saved YAML. The list survives Refresh — your manual selections are preserved across re-introspections.
- **Generation** — dropdowns for architecture, persistence, MyBatis style, Spring Boot version, Java version, output mode, OpenAPI style; checkboxes for Lombok and MapStruct; text fields for base package, project name/group, output dir.

### Version dropdowns

The **Spring Boot version** and **Java version** combos are **editable** and bidirectionally filtered: pick Java 8 and the Spring Boot combo narrows to the 2.7.x line; pick Spring Boot 3.3.5 and the Java combo narrows to 17/21. Three independent online sources back the dropdowns, each with its own 24-hour disk cache at `~/.umaboot/cache/versions.json`:

- **Spring Boot 3.x** — Spring Initializr metadata (`start.spring.io/metadata/client`); filters pre-releases + `x.x.x` pointers.
- **Spring Boot 2.x** — Maven Central search (`search.maven.org/solrsearch/select?q=...v:2.7.*`). Initializr no longer lists 2.x after its OSS EOL.
- **Java majors** — Foojay DiscoAPI (`api.foojay.io/disco/v3.0/major_versions?ga=true&maintained=true&term_of_support=lts`), the same source the Gradle Foojay resolver uses; auto-tracks new LTS releases.

Offline / proxy-blocked? Each source falls back to its most recent on-disk cache, then to a hardcoded curated list (Java `[8, 11, 17, 21]`; SB3 `[3.4.1, 3.4.0, 3.3.6, 3.3.5, 3.2.12]`; SB2 `[2.7.18, 2.7.17, 2.7.16, 2.7.15]`). You can always type a custom version directly into either combo.

### What happens when you click Generate

1. Progress indicator runs the introspect → relationship engine → table filter → generator pipeline.
2. The output directory is refreshed in the VFS so generated files appear in the Project view.
3. A balloon notification reports the file count, output directory, and the resolved `[architecture/persistence, mode]`. If the plugin auto-switched to overlay (because there's a `pom.xml` at your project root and you didn't explicitly set the mode), it labels it `(auto)`.

### Gutter run icon

`umaboot.yaml` files get a green play icon in the editor gutter. Click it to run **Generate** without leaving the file.

## FAQ

**Q: Does Umaboot own my code after I generate?**
A: No. The output is a normal Maven project. Treat it like Spring Initializr output — once generated, edit it like any other code. Re-run `apply` to layer schema changes back in.

**Q: What happens to my edits if I regenerate without protected regions?**
A: They are overwritten. Wrap user-edited blocks in `// <umaboot:protected name="...">...// </umaboot:protected>` and `apply` will preserve them.

**Q: Can I customize the generated code's templates?**
A: Yes. Pass `--templates /path/to/dir` to `generate / diff / apply`. Files in that directory override the bundled defaults using the same paths (e.g. `mvc-jpa/Entity.java.ftl`).

**Q: What if my schema has tables I don't want generated?**
A: Use `tables.exclude` (glob patterns):
```yaml
generation:
  tables:
    exclude: [audit_*, flyway_schema_history, tmp_*]
```
Or use `tables.include` to whitelist only what you want.

**Q: My ManyToMany table has extra columns. Will Umaboot generate a junction or a real entity?**
A: A "pure" junction (PK = exactly two FK columns + optional audit columns like `created_at`) becomes a `@ManyToMany`. Anything else is generated as a normal entity.

**Q: How do I work with composite primary keys?**
A: As of v0.7, Umaboot generates entity fields for all PK columns but assumes a single-column id for the `findById(id)` API. Composite-key support is on the roadmap.

**Q: Can I generate against MySQL?**
A: Yes. Set `connection.url: jdbc:mysql://...`. The driver auto-detects from the URL; override with `connection.driver: mysql` if needed.

**Q: I run `apply` and a file shows up as a "conflict". What now?**
A: It means your edits inside a protected region produced Java that doesn't parse after merging — usually because you renamed a method that the generator still emits. Either fix the protected block to be valid stand-alone, or open the file and merge by hand. The generator never silently overwrites; conflicts always exit `1`.

**Q: Is the generated project tied to Spring Boot 3?**
A: Yes for both — fully. Umaboot generates Spring Boot 3.x projects (Java 17 / 21) by default, with Jakarta EE 10 (`jakarta.persistence.*`), records, `ProblemDetail`, and the modern Spring 6 idioms. Phases L → N (v1.4 → v1.6) added Spring Boot 2.7 support for **all combinations** of architecture (MVC / Hexagonal / DDD) × persistence (JPA / MyBatis / jOOQ). Picking `javaVersion: 8` or `11` switches the templates to `javax.*` imports, classes-not-records for `ApiError` / `CursorPage` / DDD command + event types, the legacy springdoc 1.7.0 artifact, `mybatis-spring-boot-starter:2.3.2`, jOOQ 3.16.23 (last JDK 8-compatible OSS), and Hibernate 5-compatible annotations. Only `dto.style: record`, `exception.style: problemdetail`, and cursor pagination remain Spring Boot 3 only — config-load fails fast with a clear message when those are mixed with SB2.

**Q: How do I get help?**
A: Open an issue with your `umaboot.yaml` (redact passwords), the generator's stderr, and the schema definition (or DDL excerpt) of the table that misbehaves.

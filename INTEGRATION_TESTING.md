# Umaboot — Integration Testing & Limitations

This document describes how to **manually verify** the Umaboot CLI, IntelliJ plugin, and VS Code extension work end-to-end after a build or change, and lists the project's known limitations and out-of-scope items.

It complements the automated unit tests under `umaboot-core/src/test/` (77 tests across 16 classes as of v1.7) — those cover template rendering and config validation. This document covers the things you can only verify by hand: clicking buttons, dropping files into IDEs, running real generation against a real database.

---

## Table of contents

1. [Test environment setup](#1-test-environment-setup)
2. [Building the artifacts](#2-building-the-artifacts)
3. [IntelliJ plugin integration tests](#3-intellij-plugin-integration-tests)
4. [VS Code extension integration tests](#4-vs-code-extension-integration-tests)
5. [Cross-product end-to-end scenario](#5-cross-product-end-to-end-scenario)
6. [Generated-project sanity checks](#6-generated-project-sanity-checks)
7. [Known limitations](#7-known-limitations)
8. [Out-of-scope / not implemented](#8-out-of-scope--not-implemented)

---

## 1. Test environment setup

You need:

| Tool | Version | Purpose |
|---|---|---|
| **Git** | any | Clone the repo |
| **JDK 21** | 21.x | Build the project (IntelliJ Platform 2024.2.4 requires Java 21 toolchain) |
| **JDK 17** *(optional)* | 17.x | Run the CLI against generated SB3 projects (the generated project's Java version) |
| **JDK 8 / 11** *(optional)* | 8 or 11 | Test SB2 generation paths |
| **Maven** | 3.9+ | Build `umaboot-core` and `umaboot-cli` |
| **Gradle** | 8.x | Build `umaboot-intellij` |
| **Node.js** | 20+ | Build `umaboot-vscode` |
| **Docker Desktop** | latest | Spin up Postgres / MySQL test containers; required for Testcontainers-backed integration tests in generated projects |
| **IntelliJ IDEA** | 2024.2 Community or Ultimate | Run the IntelliJ plugin under test |
| **VS Code** | 1.92+ | Run the VS Code extension under test |
| **Postgres** *(local or Docker)* | 15+ | Realistic DB for introspection tests |
| **MySQL** *(optional)* | 8.0+ | Test the MySQL driver path |

### Bring up a test database

Easy mode — use the bundled Docker Compose:

```bash
docker compose -f umaboot-test-fixtures/src/main/resources/docker-compose.yml up -d postgres
```

This gives you a Postgres 15 container on `localhost:5432` with database `umaboot`, user `postgres`, password `postgres`, pre-loaded with the `customers / orders / products / tags / product_tags` sample schema.

Test it from your shell:

```bash
psql postgresql://postgres:postgres@localhost:5432/umaboot -c "\dt"
```

You should see five tables.

---

## 2. Building the artifacts

Run from the repo root:

```bash
# Core + CLI
mvn -DskipTests clean install
# CLI fat JAR ends up at: umaboot-cli/target/umaboot.jar

# IntelliJ plugin (after core is in ~/.m2)
cd umaboot-intellij
gradle :clean :buildPlugin
# Output: umaboot-intellij/build/distributions/umaboot-intellij-*.zip
cd ..

# VS Code extension
cd umaboot-vscode
npm install
npm run compile
npx @vscode/vsce package
# Output: umaboot-vscode/umaboot-0.5.0.vsix
cd ..
```

After all three commands you should have:
- `umaboot-cli/target/umaboot.jar` (~13 MB)
- `umaboot-intellij/build/distributions/umaboot-intellij-*.zip`
- `umaboot-vscode/umaboot-0.5.0.vsix` (~180 KB)

Use these three artifacts for everything below.

---

## 3. IntelliJ plugin integration tests

### Setup

1. Open IntelliJ IDEA.
2. **Settings → Plugins → ⚙ → Install Plugin from Disk** → pick `umaboot-intellij/build/distributions/umaboot-intellij-*.zip`.
3. Restart IntelliJ when prompted.
4. Open or create an empty Java/Maven project for the test workspace.

### Smoke tests

| ID | Test | Expected result | ☐ |
|---|---|---|---|
| **I-01** | After install, look at the right-gutter tool window strip | A small Umaboot icon labeled "Umaboot" is visible | ☐ |
| **I-02** | Look in the Tools menu | "Umaboot: Generate" entry with the anvil icon is present | ☐ |
| **I-03** | Open Settings → Plugins → Installed | "Umaboot" appears with a 40×40 icon and version string | ☐ |
| **I-04** | Open Settings → Tools → Umaboot | Form panel renders with Connection / Tables / Generation sections | ☐ |
| **I-05** | Settings panel: Java version dropdown | Lists `8 / 11 / 17 / 21` | ☐ |
| **I-06** | Settings panel: change Java to `8` | Spring Boot dropdown narrows to the 2.7.x curated line | ☐ |
| **I-07** | Settings panel: change Spring Boot to `3.3.5` | Java dropdown narrows back to `17 / 21` | ☐ |
| **I-08** | Settings panel: pick Spring Boot `2.7.18` | DTO style locks to `class`, Exception style locks to `envelope` | ☐ |

### Connection & introspection

| ID | Test | Expected result | ☐ |
|---|---|---|---|
| **I-10** | Pick **Database type: postgresql**, leave **Connection mode: Host**, set **Host: localhost:5432**, **Database: umaboot**, **Schema: public**, username/password, then click **Test Connection** | Green status: "PostgreSQL 15.x" or similar | ☐ |
| **I-10b** | In **Host mode**, type `?useSSL=false` (with leading `?`) into **Parameters** and click **Test Connection** | Red status: *"Parameters field must not start with '?' …"*. The probe is rejected before any JDBC call. | ☐ |
| **I-10c** | Switch to **Connection mode: URL**, paste `jdbc:postgresql://localhost:5432/umaboot` into **JDBC URL**, click **Test Connection** | Green status: same as I-10. The host/database/params card hides entirely — URL is the only source of connection info. | ☐ |
| **I-10d** | Open a project that has a pre-v0.8 `umaboot.yaml` (flat `url:` + `schema:` shape, no `mode:` key). Open Settings → Tools → Umaboot. Click **Apply**. Re-open `umaboot.yaml` | The yaml has been rewritten with `mode:`, `type:`, `host:`/`url:`, `database:` keys. The old `driver:` key is gone. The previously working configuration still connects successfully. | ☐ |
| **I-10e** | In **Host mode** fill **Database: shop**. Switch to **Connection mode: URL**. The Database field disappears with the host card. Now type a URL **without** a database (e.g. `jdbc:postgresql://localhost:5432`) and click **Test Connection** | Green probe is followed by an **amber warning**: *"Database is empty — fill in before Apply / Refresh Tables"*. The previously-typed `shop` does NOT silently survive into URL mode. | ☐ |
| **I-10f** | Same panel as I-10e. Click **Refresh Tables** | Red status: *"Please fill in Schema before refreshing tables"* (or *Database* for MySQL). No JDBC connection is opened. | ☐ |
| **I-10g** | Set Database type to mysql, Host mode, Database: `does_not_exist`. Click **Refresh Tables** | Red status: *"Failed: Database 'does_not_exist' does not exist on this MySQL server …"*. Server-side existence check (Level 2) fires before any table-level introspection. | ☐ |
| **I-11** | Test Connection with bad password | Red status with the JDBC error message | ☐ |
| **I-12** | Click **Refresh Tables** | Table-picker checkboxes populate (`customers`, `orders`, `products`, `tags`; `product_tags` filtered as junction) | ☐ |
| **I-12b** | Type `app_` in **Strip prefix from class names**, click **Apply**, then re-open `umaboot.yaml` | The yaml has `tables.classNameStripPrefix: app_`. Run Generate against a schema with `app_users`: the entity file is `User.java` (not `AppUser.java`). Tables that don't start with `app_` are left alone. | ☐ |
| **I-12c** | After Refresh Tables, **double-click** a row in the table list | Per-table customization dialog opens with that table's columns listed (name / DB type / Java type combo). Pick `Object` for a `text` column, click Save, then run Generate. The generated entity uses `Object`-typed field for that column. | ☐ |
| **I-12d** | In the same dialog, type `Account` into the **Class name (override)** field on row `app_users`, Save | YAML has `tables.overrides.app_users.className: Account`. Generate produces `Account.java` regardless of the strip-prefix setting. | ☐ |
| **I-13** | Uncheck a table → click **Apply** → reopen Settings | The unchecked table is absent from `tables.include` in `umaboot.yaml` | ☐ |

### Generation flow

| ID | Test | Expected result | ☐ |
|---|---|---|---|
| **I-20** | Settings panel: configure MVC + JPA + Java 17 + SB 3.3.5, save with `basePackage: com.test.shop` and `outputDir: ./generated` | `umaboot.yaml` written; tool window's Generate button enabled | ☐ |
| **I-21** | Run **Tools → Umaboot: Generate** | Progress indicator runs; balloon notification shows "Generated N files in ./generated [mvc/jpa, standalone]" | ☐ |
| **I-22** | Refresh project view | `generated/` folder appears with `pom.xml`, `src/main/java/com/test/shop/...` populated | ☐ |
| **I-22b** | Open the panel again, tick **Use project directory** under Output mode, click **Apply** | The Output dir text field becomes disabled. `umaboot.yaml` now contains `outputDir: .`. | ☐ |
| **I-22c** | Run **Tools → Umaboot: Generate** with the project-directory checkbox ticked | Generated files land directly in the project root (next to `umaboot.yaml`), not under `generated/`. The notification path matches the project root. | ☐ |
| **I-22d** | Set **App config format: properties** in the Settings panel and run Generate | Generated project has `src/main/resources/application.properties` (dotted-key form, same content) and **no** `application.yml`. Switching back to `yaml` and re-generating produces the inverse. | ☐ |
| **I-25** | Generate any non-JPA project (e.g. Hexagonal + MyBatis, DDD + MyBatis, MVC + jOOQ). Run `mvn -f generated/pom.xml compile` | Build succeeds. The pom contains an explicit `spring-data-commons` dependency, so the generated `PageResponse.java`'s `import org.springframework.data.domain.Page` resolves. (JPA projects don't need this — they pull `spring-data-commons` in transitively via `spring-boot-starter-data-jpa`.) | ☐ |
| **I-26** | Generate **Hexagonal + jOOQ**. Run `mvn -f generated/pom.xml compile` | jOOQ codegen produces `${basePackage}.jooq.Tables` (and per-table `Record` classes) into `target/generated-sources/jooq`. The generated `${Entity}PersistenceAdapter` (sole persistence file) compiles, importing `Tables.{TABLE}` statically and using `dsl.fetchInto(${Entity}.class)`. No `JpaEntity` / `PersistenceModel` / mapper files emitted. | ☐ |
| **I-27** | Generate **DDD + jOOQ**. Run `mvn -f generated/pom.xml compile` | The generated `${Aggregate}RepositoryImpl` compiles, using the aggregate's reconstruction constructor (no `Customer.create(...)` calls — no `CreatedEvent` recorded on rehydration). Aggregate root has the `public ${Aggregate}(allFields)` reconstruction constructor visible at line ~30. | ☐ |
| **I-28** | Replace `connection:` with `schemaFile: ./umaboot-test-fixtures/src/main/resources/fixtures/postgres/02-relationships.sql` in `umaboot.yaml`. Run `umaboot generate` with **no live DB up**. | Generation succeeds without JDBC. Generated `Customer.java`, `Order.java` etc. match what the live-DB run produces (1:1 / 1:N / M:N relationships intact). Try `persistence: jooq` and confirm config-load fails with the "requires connection" message. | ☐ |
| **I-29** | Set `connection.type: mariadb` (or use a `jdbc:mariadb://` URL). Generate. Run `mvn -f generated/pom.xml compile`. | The pom uses `org.mariadb.jdbc:mariadb-java-client` (not `mysql-connector-j`). `application.yml` has `jdbc:mariadb://` URL + `org.mariadb.jdbc.Driver`. With Docker enabled, `docker-compose.yml` uses `mariadb:11` image + `MARIADB_*` env vars. Build compiles cleanly. | ☐ |
| **I-23** | Right-click `generated/pom.xml` → "Add as Maven Project" → run `mvn package` | Build succeeds; produces a JAR | ☐ |
| **I-24** | In the generated project, run `mvn spring-boot:run` and `curl http://localhost:8080/api/customers` | Returns `200` with `PageResponse` JSON shape | ☐ |

### Auto-overlay on existing project

| ID | Test | Expected result | ☐ |
|---|---|---|---|
| **I-30** | Open an existing Spring Boot project that has `pom.xml` at the root, but no `output.mode` set in `umaboot.yaml` | Run Generate; balloon reports `[mvc/jpa, overlay (auto)]` | ☐ |
| **I-31** | Verify generated files | Files land directly in `src/main/java/...`, not in a `generated/` subfolder; existing `pom.xml` and `Application.java` are untouched | ☐ |
| **I-32** | Existing `application.yml` | Has `mybatis.mapper-locations` (or relevant overlay-merger additions) appended below an `# umaboot:overlay` marker | ☐ |

### Per-feature tests (run the matching scenario, then verify the generated file)

| ID | Config | Expected in generated files | ☐ |
|---|---|---|---|
| **I-40** | `injection.style: lombok` (with `useLombok: true`) | `ServiceImpl.java` has `@RequiredArgsConstructor`; no explicit constructor | ☐ |
| **I-41** | `injection.style: lombok` (with `useLombok: false`) | Settings panel rejects save with clear error; CLI rejects at config load | ☐ |
| **I-42** | `injection.style: autowired` | Fields use `@Autowired`; not `final`; no constructor | ☐ |
| **I-43** | `validation.style: jakarta` | `RequestDTO` has `@NotBlank`/`@Size`; controller uses `@Valid` | ☐ |
| **I-44** | `validation.style: none` | No validation annotations anywhere; no `@Valid` on controller | ☐ |
| **I-45** | `dto.style: record` (SB3) | `RequestDTO`/`ResponseDTO` are Java records, not classes | ☐ |
| **I-46** | `dto.style: record` + Java 8/11 | Config-load rejected with clear error | ☐ |
| **I-47** | `exception.style: envelope` | `ApiError.java` emitted; `GlobalExceptionHandler` returns `ResponseEntity<ApiError>` | ☐ |
| **I-48** | `audit.enabled: true` + table has `created_at` column | `Auditable.java` `@MappedSuperclass` emitted; entity extends `Auditable`; Application has `@EnableJpaAuditing` | ☐ |
| **I-49** | `softDelete.enabled: true` + table has `deleted_at` column | Entity has `@SQLDelete` + `@Where(clause = "deleted_at IS NULL")` | ☐ |
| **I-50** | `docker.enabled: true` | `Dockerfile` + `docker-compose.yml` present at project root | ☐ |
| **I-51** | `ci.style: github` | `.github/workflows/ci.yml` present | ☐ |
| **I-52** | `logging.style: json` | `logback-spring.xml` emitted under `src/main/resources`; pom adds `logstash-logback-encoder` | ☐ |
| **I-53** | `logging.correlationId: true` | `CorrelationIdFilter.java` emitted | ☐ |
| **I-54** | `tests.enabled: true` | `AbstractIntegrationTest.java` + per-entity `*IntegrationTest.java` files in `src/test/java/...`; pom adds `testcontainers` deps | ☐ |
| **I-55** | `pagination.style: cursor` (MVC + JPA) | `CursorPage.java` emitted; controller signature `findAll(@RequestParam String cursor, @RequestParam int limit)`; repository has `findByIdGreaterThanOrderByIdAsc` | ☐ |
| **I-56** | `pagination.style: cursor` + non-MVC architecture | Config-load rejects with clear error | ☐ |
| **I-57** | `security.style: jwt` with secret set | `SecurityConfig`, `JwtTokenService`, `JwtAuthenticationFilter`, `AuthController`, `LoginRequest`, `LoginResponse` emitted; pom adds `spring-boot-starter-security` + `jjwt-*` | ☐ |
| **I-58** | `security.style: jwt` without secret | Config-load rejects with `secret required` error | ☐ |
| **I-59** | `openapi.style: annotation` | `OpenApiConfig.java` emitted; controllers have `@Tag` + `@Operation`; pom has `springdoc-openapi-starter-webmvc-ui` (SB3) or `springdoc-openapi-ui:1.7.0` (SB2) | ☐ |
| **I-60** | Spring Boot 2.7.18 + Java 11 + MVC + JPA | Generated `Entity.java` uses `import javax.persistence.*;`; pom uses `<artifactId>spring-boot-starter-parent</artifactId>` with `<version>2.7.18</version>` | ☐ |

### Editor integration

| ID | Test | Expected result | ☐ |
|---|---|---|---|
| **I-70** | Open `umaboot.yaml` in the editor | Custom file icon (the umaboot wing) appears in the project view + tab | ☐ |
| **I-71** | Same file | Green play icon appears in the editor gutter near the top | ☐ |
| **I-72** | Click the gutter play icon | Generate runs without leaving the file | ☐ |
| **I-73** | Tool window stripe icon click | Tool window opens with embedded settings form + Apply / Generate / Open Settings buttons | ☐ |

### Diff / Apply / Protected regions

| ID | Test | Expected result | ☐ |
|---|---|---|---|
| **I-80** | Generate once, modify a generated `ServiceImpl.java` adding `// <umaboot:protected name="x"> ... // </umaboot:protected>` block, then run `umaboot apply` from the CLI | The protected block is preserved; surrounding code regenerated | ☐ |
| **I-81** | Run `umaboot diff --unified` after schema change | Unified diff output shows pending changes; exit code 1 | ☐ |

---

## 4. VS Code extension integration tests

### Setup

1. Install Java 17+ on the test machine. Verify with `java -version`.
2. Drop `umaboot-cli/target/umaboot.jar` somewhere stable, e.g. `C:\Tools\umaboot\umaboot.jar`.
3. In VS Code: `Ctrl+Shift+P → "Extensions: Install from VSIX..."` → pick `umaboot-vscode/umaboot-0.5.0.vsix`.
4. Open VS Code Settings (`Ctrl+,`) → search "umaboot" → set **Cli Path**: `java -jar C:\Tools\umaboot\umaboot.jar` (Windows) or `java -jar /home/you/tools/umaboot.jar` (Mac/Linux).

### Smoke tests

| ID | Test | Expected result | ☐ |
|---|---|---|---|
| **V-01** | After install + workspace open, look at the left Activity Bar | Umaboot icon (anvil/wing) appears in the strip | ☐ |
| **V-02** | Click the Umaboot icon | Dashboard view opens. If no `umaboot.yaml` in workspace, welcome view shows "Create umaboot.yaml" + "Edit configuration with form" links | ☐ |
| **V-03** | Run `Umaboot: Create umaboot.yaml` from the command palette | Seed YAML created in workspace root and opened in editor; dashboard now shows Configuration / Tables / Actions sections | ☐ |
| **V-04** | Open the generated `umaboot.yaml` | "U" badge appears next to the filename in the explorer; CodeLens row `▶ Generate ⇄ Diff ✓ Apply 🔌 Test Connection` floats above line 1 | ☐ |
| **V-05** | Look at the bottom-left status bar | `🚀 <projectName>` appears; clicking it opens the Activity Bar dashboard | ☐ |

### Form panel (the IntelliJ Settings parity surface)

| ID | Test | Expected result | ☐ |
|---|---|---|---|
| **V-10** | Click the ✏️ pencil icon in the dashboard title bar (or run `Umaboot: Edit Configuration`) | Webview tab opens titled "Umaboot — Configuration" with 8 sections: Connection / Project / Code style / Schema-aware features / Project tooling / Security / Output / Tables | ☐ |
| **V-11** | Form fields populate from current `umaboot.yaml` | All values match the YAML on disk | ☐ |
| **V-12** | Form **Project tooling** section | Visible: Docker checkbox, CI dropdown (none/github/gitlab), Logging dropdown (plain/json), Correlation-ID checkbox, Tests checkbox | ☐ |
| **V-13** | Form **Security** section | Style dropdown (none/basic/jwt). When set to `jwt`: secret + expiration fields appear. When set to `basic` or `jwt`: users textarea appears | ☐ |
| **V-14** | Change Java version to `8` | Spring Boot version auto-fills to `2.7.18`; DTO `record` option becomes hidden; Exception `problemdetail` becomes hidden | ☐ |
| **V-15** | Uncheck Use Lombok | Injection `lombok` option becomes hidden; if it was selected, falls back to `constructor` | ☐ |
| **V-16** | Change Architecture to `hexagonal` | Pagination `cursor` option becomes hidden | ☐ |
| **V-17** | Modify a field; the header status shows "● Modified" | dirty indicator visible | ☐ |
| **V-18** | Click **Revert** | Form snaps back to last-loaded values; "Modified" cleared | ☐ |
| **V-19** | Click **Save** | Toast: "Umaboot: saved umaboot.yaml"; status clears; YAML on disk reflects form values; dashboard tree refreshes automatically | ☐ |

### Form panel — introspection buttons

| ID | Test | Expected result | ☐ |
|---|---|---|---|
| **V-20** | In the form, set the JDBC URL and click **Test Connection** | Status row shows "Testing connection…" then transitions to "OK PostgreSQL 15.x" (green) or error (red) | ☐ |
| **V-21** | With a bad URL | Red error: the JDBC error message | ☐ |
| **V-22** | Click **Refresh Tables** | Status row says "Refreshing tables…"; on success, table names are appended to the Include globs textarea (deduped); status: "Imported N tables…" | ☐ |

### Tree view + actions

| ID | Test | Expected result | ☐ |
|---|---|---|---|
| **V-30** | Click **Actions → Generate** in the tree | Output channel "Umaboot" opens; CLI runs; toast on completion | ☐ |
| **V-31** | Click **Configuration** section header → inline "🔌 Test Connection" icon | Same as V-20 but result toasted instead of inline | ☐ |
| **V-32** | Click **Tables** section header → inline "🔄 Refresh Tables" | QuickPick opens listing tables; selecting one copies it to clipboard | ☐ |
| **V-33** | Edit `umaboot.yaml` directly in the editor and save | Tree refreshes within ~1 second (FileSystemWatcher) | ☐ |

### CodeLens

| ID | Test | Expected result | ☐ |
|---|---|---|---|
| **V-40** | Open `umaboot.yaml` | CodeLens row above line 1 with 4 actions | ☐ |
| **V-41** | Click `▶ Generate` | Output channel opens, generate runs, toast on completion | ☐ |
| **V-42** | Click `🔌 Test Connection` | Same as form-panel V-20 (toasted result) | ☐ |
| **V-43** | Open a non-umaboot YAML file | No CodeLens appears | ☐ |

### Persistence + theme

| ID | Test | Expected result | ☐ |
|---|---|---|---|
| **V-50** | Reload VS Code (Window: Reload Window) with the form panel open | Panel state preserved; values still in form (`retainContextWhenHidden: true`) | ☐ |
| **V-51** | Switch theme between Light/Dark | Form colors update without reload (uses CSS variables) | ☐ |
| **V-52** | High Contrast theme | All controls remain readable; focus border visible | ☐ |

---

## 5. Cross-product end-to-end scenario

Verifies both products produce equivalent output against the same database.

| Step | Action | Expected |
|---|---|---|
| 1 | Bring up the Postgres test container | `docker ps` shows `umaboot-postgres` healthy |
| 2 | In IntelliJ: install plugin, open empty Maven project, configure via Settings, click Generate | Files emitted to `./generated/` |
| 3 | Save the generated `./generated/` somewhere (e.g. `expected-output-intellij/`) | — |
| 4 | In VS Code: install vsix, open the same empty workspace folder | Activity Bar icon appears |
| 5 | Open Edit Configuration form, fill in identical values, click Save → Generate | Files emitted to `./generated/` |
| 6 | Diff `expected-output-intellij/` vs the new VSCode output | `diff -r` should be empty (or only differ in timestamp comments at file headers) |

If step 6 shows real differences, file an issue — both surfaces should produce byte-identical generated code given identical config.

---

## 6. Generated-project sanity checks

After generation, the resulting project should be a real working Spring Boot app. Verify:

| Check | Command | Expected |
|---|---|---|
| Project compiles | `mvn -f generated/pom.xml compile` | Build success |
| Tests pass *(if `tests.enabled: true`)* | `mvn -f generated/pom.xml test` | All tests green; requires Docker for Testcontainers |
| App starts | `mvn -f generated/pom.xml spring-boot:run` | Logs `Started Application in N seconds`; no startup errors |
| REST list endpoint | `curl http://localhost:8080/api/customers` | Returns 200, JSON `{ content: [], page: 0, size: 20, ... }` |
| REST create | `curl -X POST -H "Content-Type: application/json" -d '{"email":"a@b.c"}' http://localhost:8080/api/customers` | Returns 201 with the created entity |
| REST 404 | `curl -i http://localhost:8080/api/customers/999999` | Returns 404 |
| OpenAPI spec *(when `openapi.style: yaml`)* | `cat generated/src/main/resources/openapi.yaml` | Valid OpenAPI 3.x doc with all entity paths |
| Swagger UI *(when `openapi.style: annotation`)* | App running → `curl http://localhost:8080/swagger-ui.html` | Returns the Swagger UI HTML |
| JWT login *(when `security.style: jwt`)* | `curl -X POST -H "Content-Type: application/json" -d '{"username":"admin","password":"admin"}' http://localhost:8080/api/auth/login` | Returns `{ "token": "eyJ..." }` |
| JWT-protected endpoint without token | `curl -i http://localhost:8080/api/customers` | Returns 401 |
| JWT-protected endpoint with token | `curl -H "Authorization: Bearer <token>" http://localhost:8080/api/customers` | Returns 200 |

---

## 7. Known limitations

These are deliberate design choices, scope cuts, or pre-existing constraints. They're documented here so anyone evaluating Umaboot knows what they're getting.

### Configuration-level

| Limitation | Workaround | Tracking |
|---|---|---|
| `dto.shape: single` is parsed but treated as `separate`. Picking it doesn't actually merge Request and Response DTOs. | Use `dto.shape: separate` (default). | Phase F scope cut; reserved for future. |
| `dto.style: record` produces broken **WebMapper** code in **Hexagonal** + **DDD** architectures. Mappers call `request.getX()` getters which records don't have. | Use `dto.style: class` if your architecture is hex/ddd. | Pre-existing latent bug from Phase F; planned fix. |
| Cursor pagination (`pagination.style: cursor`) is **MVC + JPA + Spring Boot 3 only**. Tables with composite primary keys silently fall back to offset for that table. | Use `pagination.style: offset` for non-MVC or non-JPA combos. | Cross-validation rejects incompatible configs at config-load. |
| `injection.style: lombok` requires `useLombok: true`. | Set `useLombok: true` first. | Cross-validation rejects at config-load. |
| `exception.style: problemdetail` requires Spring Boot 3.x. | Use `envelope` on Spring Boot 2.7. | Cross-validation rejects at config-load. |
| `dto.style: record` requires Java 14+ — generally Spring Boot 3.x. | Use `class` on Spring Boot 2.7 (Java 8/11). | Cross-validation rejects at config-load. |
| Java 8 / 11 require Spring Boot 2.7.x; Java 17+ for SB3. | Plugin & form panel auto-filter dropdowns. | Cross-validation rejects at config-load. |
| `audit.enabled` and `softDelete.enabled` only generate JPA-specific annotations (`@SQLDelete`, `@Where`, `@MappedSuperclass`). For MyBatis / jOOQ the columns stay on the entity but no special handling is added to mappers. | Hand-edit MyBatis mappers to add `WHERE deleted_at IS NULL`. | Phase G JPA-only scope. |
| Composite primary keys: entity gets all PK columns, but the controller's `findById(id)` API assumes a single-column id. | Hand-edit the controller signature for composite-PK tables. | Phase A scope cut. |

### Security-level

| Limitation | Workaround |
|---|---|
| In-memory users only — no database-backed `UserDetailsService`. | Replace the generated `UserDetailsService` bean with one that queries your `users` table. |
| No OAuth2 resource server / IdP integration. | Edit the generated `SecurityConfig` to add `oauth2ResourceServer().jwt(...)`. |
| No `/register`, password reset, or refresh-token endpoints. | Add to the generated `AuthController` manually. |
| No per-controller `@PreAuthorize` rules — security is path-based (`/api/auth/**` public, `/api/**` protected). | Add `@PreAuthorize` annotations to controllers post-generation. |

### Database-level

| Limitation | Workaround |
|---|---|
| No DB migration generation. The introspected schema is not reverse-engineered to Flyway / Liquibase. | Use Flyway / Liquibase yourself; Umaboot doesn't conflict. |
| jOOQ codegen requires running `mvn compile` once before the generated project compiles (the codegen plugin runs at compile time and produces `${basePackage}.jooq.Tables`). | Documented in USAGE.md. |
| No automatic detection of role schema for security. | In-memory users are seeded from `umaboot.yaml`; for DB-backed roles, replace the `UserDetailsService` bean. |

### Tool / build-level

| Limitation | Workaround |
|---|---|
| **Umaboot itself requires Java 17+ to run**, regardless of which Java target version it generates for. The CLI fat JAR was built with `--release 17`. | Use a recent Temurin JDK on the build machine. |
| **IntelliJ plugin requires Java 21 toolchain** to *build* (IntelliJ Platform 2024.2.4 dependency). End users running the plugin only need a 2024.2 IntelliJ install. | Foojay resolver in `settings.gradle.kts` auto-downloads the toolchain. |
| **VS Code extension is not on the marketplace** — distributed only as a `.vsix` file you install manually. | `npx @vscode/vsce publish` once you have a publisher account + PAT. |
| YAML round-trip drops comments. The IntelliJ Settings panel and VS Code form panel both rewrite `umaboot.yaml` from the in-memory model on save. | Hand-edit `umaboot.yaml` directly if you want commented configs. |
| MyBatis + jOOQ on Spring Boot 2.7 use older library versions (`mybatis-spring-boot-starter:2.3.2`, `jooq:3.16.23`) — the last lines that support Java 8. | Acceptable for legacy targets; upgrade Java to bump versions. |
| Spring Boot 2.7 OSS support ended Nov 2023. Generated SB2 projects work but receive no upstream security patches. Use SB3 unless you have a hard constraint. | Pick Java 17+ + SB3 for new projects. |

### IDE-integration limitations

| Limitation | Workaround |
|---|---|
| **v0.8 connection redesign is IntelliJ-only.** The IntelliJ Settings panel ships the new `Database type` dropdown + `Host` / `URL` mode toggle (card swap) + separate `Database` / `Schema` / `Parameters` fields. The VS Code form panel still shows the v0.7 single-`URL` + `Schema/Database` field layout. Both surfaces still read and write the same `umaboot.yaml`, so legacy yamls round-trip cleanly through either editor. | Use the IntelliJ panel for the new fields if you need them, or hand-edit `umaboot.yaml` directly. VS Code parity is planned for a follow-up. |
| VS Code dashboard tree shows only a summary (Configuration / Tables / Actions). To see Docker/CI/Logging/Tests/Security values you need to open the form panel via the pencil icon. | This is intentional — full parity is in the form panel; tree is a summary. |
| VS Code Refresh Tables imports all tables into the include list — there's no per-table checkbox UI like in IntelliJ's Tables tab. | Hand-edit the include globs after import. |
| VS Code form panel's table picker doesn't auto-filter junctions — it shows whatever `umaboot list-tables` returns (which already excludes junctions by default). | Pass `--all` to the CLI manually if you need junctions; the extension doesn't expose this option. |
| File icons in VS Code use a small "U" badge instead of a custom icon — VS Code only allows full icon themes to register file-type icons, not extensions. | Cosmetic; functionality unaffected. |
| The VS Code extension does **not** bundle the CLI JAR. Users must install Java separately and configure `umaboot.cliPath`. | Documented in `umaboot-vscode/README.md`. |

---

## 8. Out-of-scope / not implemented

Features that have been requested or considered but are deliberately *not* built. If you need any of these, file an issue.

| Feature | Reason for cut |
|---|---|
| **Database migration generation** (Flyway / Liquibase) | Reverse-engineering DDL across Postgres + MySQL dialects (ENUMs, sequences, FKs, comments) is large work for low marginal value — most schema-first teams already have migrations. |
| **GUI for `dto.shape: single`** | Touches Controller, Service, and Mapper signatures end-to-end — would be a phase-sized refactor. The config option exists for future implementation. |
| **Composite-key support in REST endpoints** | `findById(id1, id2)` would need DTO + URL-template changes throughout the templates. |
| **OAuth2 / Keycloak / Auth0 integration** | Each IdP needs custom config; Umaboot's in-memory `SecurityConfig` is meant to be a starting point, not the final auth solution. |
| **Drag-and-drop DDD aggregate-root editor** | Plain text fields in YAML are explicit and version-control-friendly; visual editing adds complexity for marginal benefit. |
| **Live diff preview before apply** | The CLI's `umaboot diff` already produces unified diffs; an in-IDE preview is mostly UX polish. |
| **Custom Hibernate dialect / native query support in the generated project** | Generated code uses standard JPA. Hand-edit when you need raw SQL. |
| **Multi-module Maven output** (separate `domain`, `application`, `infrastructure`, `interfaces`) for hex/DDD | Generated projects are single-module by design. Splitting is a manual refactor. |
| **VS Code marketplace publishing** | Requires publisher account + LICENSE file + repository field. Easy to do but hasn't been done. |
| **Automated UI tests for the IntelliJ plugin / VS Code extension** | The unit tests cover template rendering deterministically; UI tests would be slow and flaky for what is mostly form-binding code. Manual test plan in this document is the substitute. |
| **Live introspection in the VS Code extension without the CLI** | The extension shells out to `umaboot test-connection` / `umaboot list-tables` because the JDBC drivers and introspection logic live in `umaboot-core` (Java). Replicating in TypeScript would mean maintaining two codebases. |
| **i18n / non-English UI strings** | All UI is English-only. |

---

## Document status

- **Last updated**: v1.7 release (Phase R.3 complete — full IntelliJ parity in VS Code).
- **Coverage**: IntelliJ plugin v0.7+, VS Code extension v0.5.0, Umaboot CLI / core through Phase O (security scaffolding).
- **Living doc**: when you ship a new feature phase, add the corresponding test row in section 3 / 4 / 5 and update the limitations / out-of-scope sections accordingly.

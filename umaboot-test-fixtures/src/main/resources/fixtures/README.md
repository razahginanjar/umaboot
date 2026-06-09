# Test fixtures

Per-condition SQL scripts for exercising the Umaboot generator against a live
PostgreSQL or MySQL instance, plus kitchen-sink sample schemas for MySQL,
MariaDB, and SQLite script-mode regression coverage. Each numbered script isolates **one** schema-side
condition that drives a different code path in the introspector / relationship
engine / generator. Pick the script that matches what you want to verify.

All scripts are **re-runnable** — each starts with `DROP … IF EXISTS` so you
can apply the same script repeatedly against the same database. Table names
across scripts are non-overlapping, so multiple scripts can coexist if you
want a kitchen-sink schema for ad-hoc exploration.

## Scenarios

| #  | Script                       | Condition exercised                                                                                  | What to verify in generated output                                                                  |
|----|------------------------------|------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------|
| 01 | `01-basic-crud.sql`          | Single table, simple PK, common scalar types                                                          | `User` entity, `UserRepository`, `UserController` smoke-generate cleanly                            |
| 02 | `02-relationships.sql`       | Self-reference, 1:1 (UNIQUE FK), 1:N, M:N pure junction, rich junction (extra column)                 | `@OneToOne`, `@OneToMany` / `@ManyToOne`, `@ManyToMany(@JoinTable)`. `product_tags` hidden as junction |
| 03 | `03-audit-columns.sql`       | `created_at` / `updated_at` / `created_by` / `updated_by` on a single table                           | `Auditable` `@MappedSuperclass`, entity extends it, `@EnableJpaAuditing`, `AuditorAwareConfig` stub |
| 04 | `04-soft-delete.sql`         | Two tables: `deleted_at` (timestamp) flavor + `is_deleted` (boolean) flavor                           | `@SQLDelete` + `@Where` on each entity with the matching clause                                     |
| 05 | `05-composite-pk.sql`        | Composite PK + composite-FK reference back to a composite PK                                          | Cursor pagination falls back to offset for these tables; embeddable id class                        |
| 06 | `06-enum-types.sql`          | Postgres `CREATE TYPE … AS ENUM` vs MySQL inline `ENUM('A','B')`                                     | Java enums emitted; columns mapped to those enum types                                              |
| 07 | `07-wide-types.sql`          | NUMERIC(20,4), TIMESTAMPTZ, JSON/JSONB, UUID/CHAR(36), BYTEA/BLOB, TEXT, defaults                     | `JavaTypeMapper` produces `BigDecimal` / `OffsetDateTime` / `UUID` / `byte[]` / `String` correctly  |
| 08 | `08-constraints.sql`         | Column UNIQUE, table UNIQUE, NOT NULL, CHECK, DEFAULT, FK `ON DELETE CASCADE`                         | `@Column(nullable=false, unique=true)`, validation annotations when `validation.style: jakarta`     |
| 09 | `09-comments.sql`            | Table + column comments on every column                                                               | OpenAPI `description:` populated; entity Javadoc on each field                                      |
| 10 | `10-naming-edge-cases.sql`   | Plural table (`people`→`Person`), versioned name (`user_v2`→`UserV2`), reserved-word columns          | Singularizer correctness; reserved-word columns escaped properly in JPA `@Column(name=…)`           |

## Layout

```
fixtures/
  postgres/
    sample-schema.sql            # original kitchen-sink (used by GenerateIntegrationTest)
    01-basic-crud.sql            …  10-naming-edge-cases.sql
  mysql/
    sample-schema.sql            # MySQL kitchen-sink script-mode regression schema
    01-basic-crud.sql            …  10-naming-edge-cases.sql
  mariadb/
    sample-schema.sql            # MariaDB kitchen-sink script-mode regression schema
  sqlite/
    sample-schema.sql            # SQLite kitchen-sink script-mode regression schema
```

`postgres/NN-name.sql` and `mysql/NN-name.sql` test the **same** condition with
the **same** logical schema, dialect-adjusted (`BIGSERIAL` ↔ `BIGINT AUTO_INCREMENT`,
`JSONB` ↔ `JSON`, `BYTEA` ↔ `BLOB`, etc.). Comparing introspection results across
the two engines for the same script is a useful regression check.

The `*/sample-schema.sql` files are broader script-mode fixtures. They all use
the same logical model (`customers`, `addresses`, `products`, `orders`,
`order_items`, `tags`, `product_tags`) and are dialect-adjusted for parser and
generation tests across Postgres, MySQL, MariaDB, and SQLite.

## Applying a script

### Bring up a database

The bundled `docker-compose.yml` (one directory up, at
`umaboot-test-fixtures/src/main/resources/docker-compose.yml`) defines both
services. Start whichever you need:

```bash
# Postgres on localhost:5432
docker compose -f umaboot-test-fixtures/src/main/resources/docker-compose.yml up -d postgres

# MySQL on localhost:3306
docker compose -f umaboot-test-fixtures/src/main/resources/docker-compose.yml up -d mysql
```

> Note: the compose file does **not** auto-load these per-condition scripts at
> container init time. Apply them manually so each test run controls which
> condition is in play.

### Apply via psql / mysql CLI

```bash
# Postgres
psql postgresql://postgres:postgres@localhost:5432/umaboot \
    -f umaboot-test-fixtures/src/main/resources/fixtures/postgres/02-relationships.sql

# MySQL
mysql -h 127.0.0.1 -P 3306 -u umaboot -pumaboot umaboot \
    < umaboot-test-fixtures/src/main/resources/fixtures/mysql/02-relationships.sql
```

### Apply from a JUnit / Testcontainers test

The `FixtureLoader` class exposes a constant for every script:

```java
import io.umaboot.fixtures.FixtureLoader;

String sql = FixtureLoader.load(FixtureLoader.POSTGRES_RELATIONSHIPS);
try (Statement st = conn.createStatement()) {
    st.execute(sql);
}
```

Iterate all scenarios for a given engine:

```java
for (String script : FixtureLoader.POSTGRES_SCENARIOS) {
    String sql = FixtureLoader.load(script);
    // apply to a fresh container, run pipeline, assert
}
```

## Adding a new scenario

1. Pick the next available number (`11-…`).
2. Add the script under both `fixtures/postgres/` and `fixtures/mysql/` with
   parallel content.
3. Add the matching `POSTGRES_*` and `MYSQL_*` constants to `FixtureLoader`,
   and append both to `POSTGRES_SCENARIOS` / `MYSQL_SCENARIOS`.
4. Add the row to the table at the top of this README.

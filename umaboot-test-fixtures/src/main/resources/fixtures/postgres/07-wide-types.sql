-- 07-wide-types (Postgres)
-- Exercises the JavaTypeMapper on the harder-to-map column types:
--   UUID         -> java.util.UUID
--   TEXT         -> String
--   NUMERIC(20,4)-> BigDecimal
--   BYTEA        -> byte[]
--   JSONB        -> String (Umaboot keeps JSON as String; see USAGE.md)
--   TIMESTAMPTZ  -> OffsetDateTime / Instant
--   BOOLEAN with DEFAULT
--   integer ARRAY (Postgres-only) -> Integer[]
-- Requires the pgcrypto extension for gen_random_uuid().
-- Re-runnable.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

DROP TABLE IF EXISTS payloads CASCADE;

CREATE TABLE payloads (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title         TEXT            NOT NULL,
    big_decimal   NUMERIC(20, 4)  NOT NULL,
    raw_bytes     BYTEA,
    metadata      JSONB,
    created_at_tz TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at_tz TIMESTAMPTZ,
    is_archived   BOOLEAN         NOT NULL DEFAULT FALSE,
    flags         INTEGER ARRAY   NOT NULL DEFAULT '{}'
);

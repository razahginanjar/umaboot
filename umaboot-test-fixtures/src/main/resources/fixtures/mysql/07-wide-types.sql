-- 07-wide-types (MySQL)
-- Mirror of postgres/07-wide-types.sql, dialect-adjusted:
--   UUID         -> CHAR(36)            (no native UUID type)
--   BYTEA        -> BLOB
--   JSONB        -> JSON                (MySQL 5.7+)
--   TIMESTAMPTZ  -> TIMESTAMP           (MySQL stores TIMESTAMP in UTC; coerce client-side)
--   BOOLEAN      -> TINYINT(1)
--   integer ARRAY-> not available; column omitted (Postgres-only feature).
-- Re-runnable.

DROP TABLE IF EXISTS payloads;

CREATE TABLE payloads (
    id            CHAR(36)      NOT NULL PRIMARY KEY,
    title         TEXT          NOT NULL,
    big_decimal   DECIMAL(20,4) NOT NULL,
    raw_bytes     BLOB,
    metadata      JSON,
    created_at_tz TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at_tz TIMESTAMP     NULL,
    is_archived   TINYINT(1)    NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

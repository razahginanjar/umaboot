-- 10-naming-edge-cases (Postgres)
-- Exercises the Naming utility on tricky identifiers:
--   * Plural table name `people`        -> entity Person (irregular singularizer)
--   * Versioned table name `user_v2`    -> entity UserV2
--   * SQL reserved words as columns     -> "order", "select" (must remain quoted in SQL,
--                                          camelCased to `order` / `select` Java fields).
--   * Mixed identifier styles           -> snake_case and tail acronym (`api_key_id`).
--   * Junction-style with multi-token   -> user_roles links user_v2 + role.
-- Re-runnable.

DROP TABLE IF EXISTS user_roles CASCADE;
DROP TABLE IF EXISTS user_v2    CASCADE;
DROP TABLE IF EXISTS people     CASCADE;

-- Plural table; Umaboot's singularizer should produce `Person`.
CREATE TABLE people (
    id            BIGSERIAL PRIMARY KEY,
    given_name    VARCHAR(100) NOT NULL,
    family_name   VARCHAR(100),
    date_of_birth DATE
);

-- Versioned name + reserved-word columns (must be quoted in DDL).
CREATE TABLE user_v2 (
    id         BIGSERIAL    PRIMARY KEY,
    user_name  VARCHAR(100) NOT NULL,
    "order"    INTEGER      NOT NULL DEFAULT 0,
    "select"   BOOLEAN      NOT NULL DEFAULT FALSE,
    api_key_id VARCHAR(64)
);

-- Junction-style table with non-trivial endpoint names.
CREATE TABLE user_roles (
    user_v2_id BIGINT NOT NULL REFERENCES user_v2(id),
    role_id    BIGINT NOT NULL,
    PRIMARY KEY (user_v2_id, role_id)
);

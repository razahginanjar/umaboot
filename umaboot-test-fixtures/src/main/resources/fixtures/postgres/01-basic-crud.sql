-- 01-basic-crud (Postgres)
-- Smoke baseline. Single table, simple BIGSERIAL PK, common scalar types.
-- Re-runnable: drops then recreates.

DROP TABLE IF EXISTS users CASCADE;

CREATE TABLE users (
    id          BIGSERIAL    PRIMARY KEY,
    email       VARCHAR(255) NOT NULL UNIQUE,
    full_name   VARCHAR(200) NOT NULL,
    age         INTEGER,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    signup_date DATE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

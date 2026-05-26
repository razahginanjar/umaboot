-- 08-constraints (Postgres)
-- Constraints exercised:
--   * column-level UNIQUE (accounts.code)
--   * table-level UNIQUE  (accounts.code, currency)
--   * NOT NULL + DEFAULT  (multiple)
--   * column-level CHECK  (balance >= 0, amount > 0, kind IN (...))
--   * FOREIGN KEY ... ON DELETE CASCADE (transactions -> accounts)
-- These should all flow through to JPA validation annotations
-- (when validation.style: jakarta) and to the entity column metadata.
-- Re-runnable.

DROP TABLE IF EXISTS transactions CASCADE;
DROP TABLE IF EXISTS accounts     CASCADE;

CREATE TABLE accounts (
    id        BIGSERIAL    PRIMARY KEY,
    code      VARCHAR(32)  NOT NULL UNIQUE,
    balance   NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    currency  CHAR(3)      NOT NULL DEFAULT 'USD',
    is_locked BOOLEAN      NOT NULL DEFAULT FALSE,
    UNIQUE (code, currency)
);

CREATE TABLE transactions (
    id          BIGSERIAL     PRIMARY KEY,
    account_id  BIGINT        NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    kind        VARCHAR(10)   NOT NULL CHECK (kind IN ('DEBIT', 'CREDIT')),
    amount      NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    occurred_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

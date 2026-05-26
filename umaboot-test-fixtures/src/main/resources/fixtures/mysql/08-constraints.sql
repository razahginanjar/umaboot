-- 08-constraints (MySQL)
-- Mirror of postgres/08-constraints.sql. CHECK constraints are syntactically
-- accepted on MySQL 5.7+ and ENFORCED on MySQL 8.0.16+ — the generator only
-- needs to read them via INFORMATION_SCHEMA, so this is dialect-portable.
-- Re-runnable.

DROP TABLE IF EXISTS transactions;
DROP TABLE IF EXISTS accounts;

CREATE TABLE accounts (
    id        BIGINT        AUTO_INCREMENT PRIMARY KEY,
    code      VARCHAR(32)   NOT NULL UNIQUE,
    balance   DECIMAL(15,2) NOT NULL DEFAULT 0,
    currency  CHAR(3)       NOT NULL DEFAULT 'USD',
    is_locked TINYINT(1)    NOT NULL DEFAULT 0,
    UNIQUE (code, currency),
    CONSTRAINT chk_accounts_balance_nonneg CHECK (balance >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE transactions (
    id          BIGINT        AUTO_INCREMENT PRIMARY KEY,
    account_id  BIGINT        NOT NULL,
    kind        VARCHAR(10)   NOT NULL,
    amount      DECIMAL(15,2) NOT NULL,
    occurred_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_tx_kind   CHECK (kind IN ('DEBIT', 'CREDIT')),
    CONSTRAINT chk_tx_amount CHECK (amount > 0),
    FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

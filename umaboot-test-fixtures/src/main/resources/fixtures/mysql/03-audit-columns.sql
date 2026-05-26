-- 03-audit-columns (MySQL)
-- Mirror of postgres/03-audit-columns.sql. Audit auto-detection is engine-agnostic:
-- the introspector looks for the column names regardless of dialect.
-- Re-runnable.

DROP TABLE IF EXISTS articles;

CREATE TABLE articles (
    id         BIGINT       AUTO_INCREMENT PRIMARY KEY,
    title      VARCHAR(255) NOT NULL,
    body       TEXT         NOT NULL,
    -- Audit columns (auto-detected when audit.enabled: true)
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 04-soft-delete (MySQL)
-- Mirror of postgres/04-soft-delete.sql:
--   * documents.deleted_at (DATETIME) -> @Where(clause = "deleted_at IS NULL")
--   * posts.is_deleted     (TINYINT(1)) -> @Where(clause = "is_deleted = false")
-- JPA only — see "Known limitations" in INTEGRATION_TESTING.md.
-- Re-runnable.

DROP TABLE IF EXISTS documents;
DROP TABLE IF EXISTS posts;

-- Variant A: timestamp soft-delete (NULL = active, non-NULL = deleted at)
CREATE TABLE documents (
    id         BIGINT       AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    content    TEXT,
    deleted_at DATETIME     NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Variant B: boolean soft-delete
CREATE TABLE posts (
    id         BIGINT       AUTO_INCREMENT PRIMARY KEY,
    title      VARCHAR(255) NOT NULL,
    body       TEXT,
    is_deleted TINYINT(1)   NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

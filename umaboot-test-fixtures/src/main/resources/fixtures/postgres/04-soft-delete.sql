-- 04-soft-delete (Postgres)
-- Two tables exercising both supported soft-delete shapes:
--   * documents.deleted_at (TIMESTAMP)  -> @Where(clause = "deleted_at IS NULL")
--   * posts.is_deleted     (BOOLEAN)    -> @Where(clause = "is_deleted = false")
-- Both also get @SQLDelete so repository.delete() rewrites to UPDATE.
-- JPA only — see "Known limitations" in INTEGRATION_TESTING.md.
-- Re-runnable.

DROP TABLE IF EXISTS documents CASCADE;
DROP TABLE IF EXISTS posts     CASCADE;

-- Variant A: timestamp soft-delete (NULL = active, non-NULL = deleted at)
CREATE TABLE documents (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    content    TEXT,
    deleted_at TIMESTAMP
);

-- Variant B: boolean soft-delete
CREATE TABLE posts (
    id         BIGSERIAL PRIMARY KEY,
    title      VARCHAR(255) NOT NULL,
    body       TEXT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

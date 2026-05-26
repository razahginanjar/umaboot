-- 03-audit-columns (Postgres)
-- Tests audit.enabled auto-detection. With the defaults
-- (audit.createdAt=created_at / updatedAt=updated_at / createdBy=created_by / updatedBy=updated_by)
-- Umaboot should:
--   * extend the entity from a generated `Auditable` @MappedSuperclass
--   * emit @EnableJpaAuditing on the Application class
--   * emit AuditorAwareConfig because created_by/updated_by are present
--   * REMOVE the four audit columns from the per-entity field list
-- Re-runnable.

DROP TABLE IF EXISTS articles CASCADE;

CREATE TABLE articles (
    id         BIGSERIAL PRIMARY KEY,
    title      VARCHAR(255) NOT NULL,
    body       TEXT         NOT NULL,
    -- Audit columns (auto-detected when audit.enabled: true)
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

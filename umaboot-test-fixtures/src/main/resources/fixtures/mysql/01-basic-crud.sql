-- 01-basic-crud (MySQL)
-- Smoke baseline. Mirror of postgres/01-basic-crud.sql, dialect-adjusted:
--   BIGSERIAL  -> BIGINT AUTO_INCREMENT
--   BOOLEAN    -> TINYINT(1)
--   TIMESTAMP DEFAULT CURRENT_TIMESTAMP works on MySQL 5.7+
-- Re-runnable.

DROP TABLE IF EXISTS users;

CREATE TABLE users (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    email       VARCHAR(255) NOT NULL UNIQUE,
    full_name   VARCHAR(200) NOT NULL,
    age         INT,
    is_active   TINYINT(1)   NOT NULL DEFAULT 1,
    signup_date DATE,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

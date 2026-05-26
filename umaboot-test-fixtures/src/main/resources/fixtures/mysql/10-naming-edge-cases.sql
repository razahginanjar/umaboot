-- 10-naming-edge-cases (MySQL)
-- Mirror of postgres/10-naming-edge-cases.sql, dialect-adjusted:
--   * Reserved-word columns "order"/"select" -> backtick-quoted in MySQL
--   * BOOLEAN -> TINYINT(1)
-- Re-runnable.

DROP TABLE IF EXISTS user_roles;
DROP TABLE IF EXISTS user_v2;
DROP TABLE IF EXISTS people;

-- Plural table; Umaboot's singularizer should produce `Person`.
CREATE TABLE people (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
    given_name    VARCHAR(100) NOT NULL,
    family_name   VARCHAR(100),
    date_of_birth DATE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Versioned name + reserved-word columns (must be backtick-quoted in DDL).
CREATE TABLE user_v2 (
    id         BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_name  VARCHAR(100) NOT NULL,
    `order`    INT          NOT NULL DEFAULT 0,
    `select`   TINYINT(1)   NOT NULL DEFAULT 0,
    api_key_id VARCHAR(64)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Junction-style table with non-trivial endpoint names.
CREATE TABLE user_roles (
    user_v2_id BIGINT NOT NULL,
    role_id    BIGINT NOT NULL,
    PRIMARY KEY (user_v2_id, role_id),
    FOREIGN KEY (user_v2_id) REFERENCES user_v2(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

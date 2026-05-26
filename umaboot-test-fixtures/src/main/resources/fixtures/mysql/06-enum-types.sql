-- 06-enum-types (MySQL)
-- MySQL's inline column-level ENUM(...). Distinct from Postgres CREATE TYPE.
-- The MySQL introspector parses the INFORMATION_SCHEMA.COLUMNS COLUMN_TYPE
-- string (e.g. "enum('PENDING','IN_TRANSIT')") to extract the values.
-- See MysqlEnumLiteralParserTest for the parsing rules.
-- Re-runnable.

DROP TABLE IF EXISTS shipments;

CREATE TABLE shipments (
    id        BIGINT       AUTO_INCREMENT PRIMARY KEY,
    tracking  VARCHAR(64)  NOT NULL UNIQUE,
    status    ENUM('PENDING', 'IN_TRANSIT', 'DELIVERED', 'RETURNED') NOT NULL DEFAULT 'PENDING',
    carrier   ENUM('UPS', 'FEDEX', 'DHL', 'USPS') NOT NULL,
    weight_kg DECIMAL(8,3) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

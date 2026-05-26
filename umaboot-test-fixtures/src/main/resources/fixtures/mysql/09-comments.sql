-- 09-comments (MySQL)
-- MySQL inline COMMENT syntax (vs Postgres's COMMENT ON TABLE / COLUMN).
-- The MysqlIntrospector reads these from INFORMATION_SCHEMA.TABLES.TABLE_COMMENT
-- and INFORMATION_SCHEMA.COLUMNS.COLUMN_COMMENT.
-- Re-runnable.

DROP TABLE IF EXISTS books;

CREATE TABLE books (
    id     BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT 'Surrogate primary key',
    title  VARCHAR(255) NOT NULL                   COMMENT 'Public title displayed on listings',
    isbn   VARCHAR(20)  UNIQUE                     COMMENT 'ISBN-13 in canonical hyphenated form',
    pages  INT          NOT NULL                   COMMENT 'Total page count',
    rating DECIMAL(3,2)                            COMMENT 'Average reader rating between 0.00 and 5.00',
    CONSTRAINT chk_books_pages_positive  CHECK (pages > 0),
    CONSTRAINT chk_books_rating_range    CHECK (rating IS NULL OR (rating >= 0 AND rating <= 5))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Books available in the catalog';

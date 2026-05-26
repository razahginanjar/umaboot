-- 09-comments (Postgres)
-- COMMENT ON TABLE / COLUMN drive OpenAPI descriptions and JavaDoc on the
-- generated entities. Tests that PostgresIntrospector.loadTableComments and
-- loadColumnComments fully populate ColumnModel#comment / TableModel#comment.
-- Re-runnable.

DROP TABLE IF EXISTS books CASCADE;

CREATE TABLE books (
    id     BIGSERIAL    PRIMARY KEY,
    title  VARCHAR(255) NOT NULL,
    isbn   VARCHAR(20)  UNIQUE,
    pages  INTEGER      NOT NULL CHECK (pages > 0),
    rating NUMERIC(3,2) CHECK (rating >= 0 AND rating <= 5)
);

COMMENT ON TABLE  books        IS 'Books available in the catalog';
COMMENT ON COLUMN books.id     IS 'Surrogate primary key';
COMMENT ON COLUMN books.title  IS 'Public title displayed on listings';
COMMENT ON COLUMN books.isbn   IS 'ISBN-13 in canonical hyphenated form';
COMMENT ON COLUMN books.pages  IS 'Total page count';
COMMENT ON COLUMN books.rating IS 'Average reader rating between 0.00 and 5.00';

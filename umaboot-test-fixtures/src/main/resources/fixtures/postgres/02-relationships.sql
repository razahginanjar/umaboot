-- 02-relationships (Postgres)
-- Exercises the full RelationshipEngine matrix:
--   * self-reference  : customers.parent_id -> customers.id
--   * 1:1             : addresses.customer_id UNIQUE FK -> customers.id
--   * 1:N             : orders.customer_id FK -> customers.id
--   * pure M:N        : product_tags (PK = exactly the two FKs) -> emitted as @ManyToMany
--   * rich junction   : order_items has an extra non-audit column (quantity), so the
--                       relationship engine treats it as a domain entity, not a junction.
-- Re-runnable.

DROP TABLE IF EXISTS product_tags CASCADE;
DROP TABLE IF EXISTS order_items  CASCADE;
DROP TABLE IF EXISTS orders       CASCADE;
DROP TABLE IF EXISTS addresses    CASCADE;
DROP TABLE IF EXISTS customers    CASCADE;
DROP TABLE IF EXISTS products     CASCADE;
DROP TABLE IF EXISTS tags         CASCADE;

CREATE TABLE customers (
    id        BIGSERIAL PRIMARY KEY,
    email     VARCHAR(255) NOT NULL UNIQUE,
    full_name VARCHAR(200) NOT NULL,
    parent_id BIGINT REFERENCES customers(id)        -- self-reference
);

CREATE TABLE addresses (
    id          BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL UNIQUE
                       REFERENCES customers(id),     -- 1:1 (UNIQUE FK)
    street      VARCHAR(500) NOT NULL,
    city        VARCHAR(100) NOT NULL,
    country     CHAR(2)      NOT NULL
);

CREATE TABLE products (
    id    BIGSERIAL PRIMARY KEY,
    sku   VARCHAR(64)    NOT NULL UNIQUE,
    name  VARCHAR(200)   NOT NULL,
    price NUMERIC(12, 2) NOT NULL
);

CREATE TABLE orders (
    id          BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers(id),  -- 1:N
    total       NUMERIC(12, 2) NOT NULL,
    placed_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Rich junction (NOT a pure junction — has the extra `quantity` column).
CREATE TABLE order_items (
    order_id   BIGINT  NOT NULL REFERENCES orders(id),
    product_id BIGINT  NOT NULL REFERENCES products(id),
    quantity   INTEGER NOT NULL,
    PRIMARY KEY (order_id, product_id)
);

CREATE TABLE tags (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

-- Pure junction — PK is exactly the two FKs, no extra columns.
-- Umaboot's RelationshipEngine will hide this and emit @ManyToMany on Product↔Tag.
CREATE TABLE product_tags (
    product_id BIGINT NOT NULL REFERENCES products(id),
    tag_id     BIGINT NOT NULL REFERENCES tags(id),
    PRIMARY KEY (product_id, tag_id)
);

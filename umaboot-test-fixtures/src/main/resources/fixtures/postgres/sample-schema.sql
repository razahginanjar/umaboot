-- CRUDForge integration-test fixture schema.
-- Exercises: ENUM types, comments, 1:1, 1:N, M:N (junction), self-reference.

CREATE TYPE order_status AS ENUM ('PENDING', 'PAID', 'SHIPPED', 'CANCELLED');

CREATE TABLE customers (
    id          BIGSERIAL PRIMARY KEY,
    email       VARCHAR(255) NOT NULL UNIQUE,
    full_name   VARCHAR(200) NOT NULL,
    parent_id   BIGINT REFERENCES customers(id),  -- self-reference
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE customers IS 'Registered customers';
COMMENT ON COLUMN customers.email IS 'Login email, unique per tenant';

CREATE TABLE addresses (
    id           BIGSERIAL PRIMARY KEY,
    customer_id  BIGINT NOT NULL UNIQUE REFERENCES customers(id),  -- 1:1
    street       VARCHAR(500) NOT NULL,
    city         VARCHAR(100) NOT NULL,
    country      VARCHAR(2)   NOT NULL
);

CREATE TABLE products (
    id          BIGSERIAL PRIMARY KEY,
    sku         VARCHAR(64) NOT NULL UNIQUE,
    name        VARCHAR(200) NOT NULL,
    price       NUMERIC(12, 2) NOT NULL
);

CREATE TABLE orders (
    id          BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers(id), -- 1:N
    status      order_status NOT NULL DEFAULT 'PENDING',
    total       NUMERIC(12, 2) NOT NULL,
    placed_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE order_items (
    order_id   BIGINT NOT NULL REFERENCES orders(id),
    product_id BIGINT NOT NULL REFERENCES products(id),
    quantity   INTEGER NOT NULL,
    PRIMARY KEY (order_id, product_id)
);
-- order_items is a near-junction; quantity makes it a domain entity in v2.
-- For v0.1 the RelationshipEngine will treat it as not-a-pure-junction
-- because of the extra non-audit column.

CREATE TABLE tags (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE product_tags (
    product_id BIGINT NOT NULL REFERENCES products(id),
    tag_id     BIGINT NOT NULL REFERENCES tags(id),
    PRIMARY KEY (product_id, tag_id)
);
-- product_tags is a pure junction (only PK = 2 FKs) -> ManyToMany generated.

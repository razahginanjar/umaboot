-- CRUDForge integration-test fixture schema.
-- Exercises: ENUM types, comments, 1:1, 1:N, M:N (junction),
-- self-reference, optional FKs, composite FKs, unique constraints, and indexes.

DROP TYPE IF EXISTS order_status CASCADE;
DROP TABLE IF EXISTS customers CASCADE;
DROP TABLE IF EXISTS addresses CASCADE;
DROP TABLE IF EXISTS products CASCADE;
DROP TABLE IF EXISTS orders CASCADE;
DROP TABLE IF EXISTS order_item_audits CASCADE;
DROP TABLE IF EXISTS order_items CASCADE;
DROP TABLE IF EXISTS tags CASCADE;
DROP TABLE IF EXISTS product_tags CASCADE;


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
    price       NUMERIC(12, 2) NOT NULL,
    CONSTRAINT ux_products_sku_name UNIQUE (sku, name)
);

CREATE TABLE orders (
    id                 BIGSERIAL PRIMARY KEY,
    customer_id        BIGINT NOT NULL REFERENCES customers(id), -- 1:N
    billing_address_id BIGINT REFERENCES addresses(id), -- optional FK
    status             order_status NOT NULL DEFAULT 'PENDING',
    total              NUMERIC(12, 2) NOT NULL,
    placed_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
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

CREATE TABLE order_item_audits (
    id         BIGSERIAL PRIMARY KEY,
    order_id   BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    note       VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_item_audits_item
        FOREIGN KEY (order_id, product_id) REFERENCES order_items(order_id, product_id)
);
-- Composite FK back to a composite PK table; should be a normal entity, not a junction.

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

CREATE INDEX idx_customers_email_created_at ON customers (email, created_at);
CREATE INDEX idx_orders_customer_status ON orders (customer_id, status);
CREATE INDEX idx_orders_billing_address ON orders (billing_address_id);
CREATE INDEX idx_order_items_product ON order_items (product_id);
CREATE INDEX idx_order_item_audits_item ON order_item_audits (order_id, product_id);
CREATE INDEX idx_product_tags_tag_product ON product_tags (tag_id, product_id);

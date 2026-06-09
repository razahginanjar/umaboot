-- SQLite kitchen-sink schema for script-mode generation tests.
-- Covers rowid identity syntax, self-reference, 1:1, 1:N, rich junction,
-- pure junction, CHECK-backed status fields, optional FKs, composite FKs,
-- secondary indexes, and SQLite storage classes.

DROP TABLE IF EXISTS product_tags;
DROP TABLE IF EXISTS order_item_audits;
DROP TABLE IF EXISTS order_items;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS addresses;
DROP TABLE IF EXISTS customers;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS tags;

CREATE TABLE customers (
    id         INTEGER PRIMARY KEY,
    email      TEXT NOT NULL UNIQUE,
    full_name  TEXT NOT NULL,
    status     TEXT NOT NULL DEFAULT 'ACTIVE'
               CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED')),
    parent_id  INTEGER,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (parent_id) REFERENCES customers(id)
);

CREATE TABLE addresses (
    id          INTEGER PRIMARY KEY,
    customer_id INTEGER NOT NULL UNIQUE,
    street      TEXT NOT NULL,
    city        TEXT NOT NULL,
    country     TEXT NOT NULL,
    FOREIGN KEY (customer_id) REFERENCES customers(id)
);

CREATE TABLE products (
    id         INTEGER PRIMARY KEY,
    sku        TEXT NOT NULL UNIQUE,
    name       TEXT NOT NULL,
    price      NUMERIC(12, 2) NOT NULL,
    metadata   TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_products_sku_name UNIQUE (sku, name)
);

CREATE TABLE orders (
    id                 INTEGER PRIMARY KEY,
    customer_id        INTEGER NOT NULL,
    billing_address_id INTEGER,
    status             TEXT NOT NULL DEFAULT 'DRAFT'
                       CHECK (status IN ('DRAFT', 'PAID', 'SHIPPED', 'CANCELLED')),
    total              NUMERIC(12, 2) NOT NULL,
    placed_at          TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES customers(id),
    FOREIGN KEY (billing_address_id) REFERENCES addresses(id)
);

-- Near-junction; quantity makes it a domain entity, not a pure link table.
CREATE TABLE order_items (
    order_id   INTEGER NOT NULL,
    product_id INTEGER NOT NULL,
    quantity   INTEGER NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    PRIMARY KEY (order_id, product_id),
    FOREIGN KEY (order_id) REFERENCES orders(id),
    FOREIGN KEY (product_id) REFERENCES products(id)
);

CREATE TABLE order_item_audits (
    id         INTEGER PRIMARY KEY,
    order_id   INTEGER NOT NULL,
    product_id INTEGER NOT NULL,
    event_type TEXT NOT NULL,
    note       TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (order_id, product_id) REFERENCES order_items(order_id, product_id)
);

CREATE TABLE tags (
    id   INTEGER PRIMARY KEY,
    name TEXT NOT NULL UNIQUE
);

-- Pure junction; PK is exactly the two FKs, so generation should not create ProductTag.
CREATE TABLE product_tags (
    product_id INTEGER NOT NULL,
    tag_id     INTEGER NOT NULL,
    PRIMARY KEY (product_id, tag_id),
    FOREIGN KEY (product_id) REFERENCES products(id),
    FOREIGN KEY (tag_id) REFERENCES tags(id)
);

CREATE INDEX idx_customers_status_created_at ON customers (status, created_at);
CREATE INDEX idx_orders_customer_status ON orders (customer_id, status);
CREATE INDEX idx_orders_billing_address ON orders (billing_address_id);
CREATE INDEX idx_order_items_product ON order_items (product_id);
CREATE INDEX idx_order_item_audits_item ON order_item_audits (order_id, product_id);
CREATE INDEX idx_product_tags_tag_product ON product_tags (tag_id, product_id);

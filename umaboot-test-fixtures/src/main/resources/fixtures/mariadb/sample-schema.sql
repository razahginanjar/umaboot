-- MariaDB kitchen-sink schema for script-mode generation tests.
-- Similar to the MySQL sample, with MariaDB-compatible JSON alias behavior
-- represented as LONGTEXT plus a JSON_VALID check, optional FKs,
-- composite FKs, and secondary indexes.

DROP TABLE IF EXISTS product_tags;
DROP TABLE IF EXISTS order_item_audits;
DROP TABLE IF EXISTS order_items;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS addresses;
DROP TABLE IF EXISTS customers;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS tags;

CREATE TABLE customers (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    email      VARCHAR(255) NOT NULL UNIQUE,
    full_name  VARCHAR(200) NOT NULL,
    status     ENUM('ACTIVE', 'SUSPENDED', 'DELETED') NOT NULL DEFAULT 'ACTIVE',
    parent_id  BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_customers_parent FOREIGN KEY (parent_id) REFERENCES customers(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Customers that can reference a parent account';

CREATE TABLE addresses (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL UNIQUE,
    street      VARCHAR(500) NOT NULL,
    city        VARCHAR(100) NOT NULL,
    country     CHAR(2) NOT NULL,
    CONSTRAINT fk_addresses_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE products (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    sku        VARCHAR(64) NOT NULL UNIQUE,
    name       VARCHAR(200) NOT NULL,
    price      DECIMAL(12, 2) NOT NULL,
    metadata   LONGTEXT NULL CHECK (metadata IS NULL OR JSON_VALID(metadata)),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_products_sku_name UNIQUE (sku, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE orders (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id        BIGINT NOT NULL,
    billing_address_id BIGINT NULL,
    status             ENUM('DRAFT', 'PAID', 'SHIPPED', 'CANCELLED') NOT NULL DEFAULT 'DRAFT',
    total              DECIMAL(12, 2) NOT NULL,
    placed_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_orders_billing_address FOREIGN KEY (billing_address_id) REFERENCES addresses(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Near-junction; quantity makes it a domain entity, not a pure link table.
CREATE TABLE order_items (
    order_id   BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity   INT NOT NULL,
    unit_price DECIMAL(12, 2) NOT NULL,
    PRIMARY KEY (order_id, product_id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE order_item_audits (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id   BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    note       VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_item_audits_item
        FOREIGN KEY (order_id, product_id) REFERENCES order_items(order_id, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tags (
    id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Pure junction; PK is exactly the two FKs, so generation should not create ProductTag.
CREATE TABLE product_tags (
    product_id BIGINT NOT NULL,
    tag_id     BIGINT NOT NULL,
    PRIMARY KEY (product_id, tag_id),
    CONSTRAINT fk_product_tags_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_product_tags_tag FOREIGN KEY (tag_id) REFERENCES tags(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_customers_status_created_at ON customers (status, created_at);
CREATE INDEX idx_orders_customer_status ON orders (customer_id, status);
CREATE INDEX idx_orders_billing_address ON orders (billing_address_id);
CREATE INDEX idx_order_items_product ON order_items (product_id);
CREATE INDEX idx_order_item_audits_item ON order_item_audits (order_id, product_id);
CREATE INDEX idx_product_tags_tag_product ON product_tags (tag_id, product_id);

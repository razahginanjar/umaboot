-- 02-relationships (MySQL)
-- Mirror of postgres/02-relationships.sql with MySQL dialect:
--   BIGSERIAL -> BIGINT AUTO_INCREMENT
--   No CASCADE on DROP TABLE; we drop in reverse-FK order instead.
--   FK declarations are explicit table-level FOREIGN KEY clauses.
-- Re-runnable.

DROP TABLE IF EXISTS product_tags;
DROP TABLE IF EXISTS order_items;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS addresses;
DROP TABLE IF EXISTS customers;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS tags;

CREATE TABLE customers (
    id        BIGINT       AUTO_INCREMENT PRIMARY KEY,
    email     VARCHAR(255) NOT NULL UNIQUE,
    full_name VARCHAR(200) NOT NULL,
    parent_id BIGINT,                                    -- self-reference
    FOREIGN KEY (parent_id) REFERENCES customers(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE addresses (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT       NOT NULL UNIQUE,            -- 1:1 (UNIQUE FK)
    street      VARCHAR(500) NOT NULL,
    city        VARCHAR(100) NOT NULL,
    country     CHAR(2)      NOT NULL,
    FOREIGN KEY (customer_id) REFERENCES customers(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE products (
    id    BIGINT         AUTO_INCREMENT PRIMARY KEY,
    sku   VARCHAR(64)    NOT NULL UNIQUE,
    name  VARCHAR(200)   NOT NULL,
    price DECIMAL(12, 2) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE orders (
    id          BIGINT         AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT         NOT NULL,                 -- 1:N
    total       DECIMAL(12, 2) NOT NULL,
    placed_at   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES customers(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Rich junction (NOT a pure junction — has the extra `quantity` column).
CREATE TABLE order_items (
    order_id   BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity   INT    NOT NULL,
    PRIMARY KEY (order_id, product_id),
    FOREIGN KEY (order_id)   REFERENCES orders(id),
    FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tags (
    id   BIGINT      AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Pure junction — PK is exactly the two FKs, no extra columns.
CREATE TABLE product_tags (
    product_id BIGINT NOT NULL,
    tag_id     BIGINT NOT NULL,
    PRIMARY KEY (product_id, tag_id),
    FOREIGN KEY (product_id) REFERENCES products(id),
    FOREIGN KEY (tag_id)     REFERENCES tags(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

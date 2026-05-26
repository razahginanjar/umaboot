-- 06-enum-types (Postgres)
-- Postgres's native ENUM type — declared with CREATE TYPE ... AS ENUM (...).
-- The Postgres introspector reads these from pg_type and surfaces them as
-- enum columns on the entity (Umaboot generates a Java enum for each).
-- Re-runnable: drops table first, then types (table depends on types).

DROP TABLE IF EXISTS shipments       CASCADE;
DROP TYPE  IF EXISTS shipment_status;
DROP TYPE  IF EXISTS carrier_name;

CREATE TYPE shipment_status AS ENUM ('PENDING', 'IN_TRANSIT', 'DELIVERED', 'RETURNED');
CREATE TYPE carrier_name    AS ENUM ('UPS',     'FEDEX',      'DHL',       'USPS');

CREATE TABLE shipments (
    id        BIGSERIAL PRIMARY KEY,
    tracking  VARCHAR(64)     NOT NULL UNIQUE,
    status    shipment_status NOT NULL DEFAULT 'PENDING',
    carrier   carrier_name    NOT NULL,
    weight_kg NUMERIC(8, 3)   NOT NULL
);

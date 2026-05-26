-- V2: Orders table
-- Stores the confirmed state of each order aggregate after the placement workflow completes.
-- UUID primary key keeps identifiers globally unique without a central sequence, which matters
-- when multiple application nodes can create orders concurrently.
-- @version enables optimistic locking — concurrent updates fail fast with HTTP 409 rather than
-- silently overwriting each other.

CREATE TABLE IF NOT EXISTS orders (
    id          UUID            NOT NULL,
    customer_id VARCHAR(100)    NOT NULL,
    product_id  UUID            NOT NULL,
    quantity    INTEGER         NOT NULL CHECK (quantity > 0),
    amount      NUMERIC(19, 2)  NOT NULL CHECK (amount >= 0),
    currency    CHAR(3)         NOT NULL DEFAULT 'USD',
    status      VARCHAR(20)     NOT NULL,
    payment_ref VARCHAR(255),
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    version     BIGINT          NOT NULL DEFAULT 0,

    CONSTRAINT pk_orders                PRIMARY KEY (id),
    CONSTRAINT fk_orders_product        FOREIGN KEY (product_id) REFERENCES products (id)
);

-- customer_id: primary access pattern for "show me my orders"
CREATE INDEX IF NOT EXISTS idx_orders_customer_id ON orders (customer_id);

-- status: admin and fulfilment queries filter by PENDING / CONFIRMED / FAILED
CREATE INDEX IF NOT EXISTS idx_orders_status      ON orders (status);

-- created_at DESC: default sort for paginated listing, matches the JPA Pageable default
CREATE INDEX IF NOT EXISTS idx_orders_created_at  ON orders (created_at DESC);

-- V1: Products table
-- Using UUID as primary key for distributed systems compatibility.
-- @version column enables optimistic locking (Section 3).
-- Indexes: category for filter queries, created_at for sorting.

CREATE TABLE IF NOT EXISTS products (
    id          UUID            NOT NULL,
    name        VARCHAR(100)    NOT NULL,
    description VARCHAR(500)    NOT NULL,
    price       NUMERIC(19, 2)  NOT NULL,
    currency    CHAR(3)         NOT NULL DEFAULT 'USD',
    stock       INTEGER         NOT NULL CHECK (stock >= 0),
    category    VARCHAR(50)     NOT NULL,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    version     BIGINT          NOT NULL DEFAULT 0,

    CONSTRAINT pk_products PRIMARY KEY (id),
    CONSTRAINT chk_price_positive CHECK (price > 0)
);

CREATE INDEX IF NOT EXISTS idx_products_category    ON products (category);
CREATE INDEX IF NOT EXISTS idx_products_created_at  ON products (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_products_stock       ON products (stock) WHERE stock < 10;

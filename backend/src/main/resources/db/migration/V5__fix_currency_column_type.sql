-- V5: Convert orders.currency from CHAR(3) to VARCHAR(3).
--
-- CHAR(3) pads values with trailing spaces to fill the fixed length.
-- Hibernate maps String fields to VARCHAR by default and its schema validator
-- rejects bpchar (PostgreSQL's internal name for CHAR) when VARCHAR is expected.
-- VARCHAR(3) stores the same 3-character currency codes without padding and
-- satisfies both the Hibernate validator and application semantics.
--
-- ALTER TYPE is safe: CHAR(3) and VARCHAR(3) share the same storage format
-- when the values have no trailing spaces, so no data is rewritten.
ALTER TABLE orders
    ALTER COLUMN currency TYPE VARCHAR(3);

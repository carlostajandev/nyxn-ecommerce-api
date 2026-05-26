-- V3: Analytical views and supporting indexes for reporting queries.
--
-- Design decisions:
--   - Views instead of materialized views: the dataset is small and queries
--     are low-frequency; materialization would add refresh complexity for
--     no measurable gain at this scale.
--   - Window functions (ROW_NUMBER, RANK, LAG) require PostgreSQL 8.4+; we
--     target 16, so all syntax here is fully supported.
--   - Oracle equivalents are documented inline as comments — syntax is
--     identical for most window functions; differences are noted explicitly.

-- ─── View 1: Top 5 best-selling products per category ─────────────────────────
-- Uses RANK() instead of ROW_NUMBER() so that ties within a category are not
-- broken arbitrarily. A product with the same order count as another at rank 1
-- is also rank 1 — both are "best-selling" and neither should be hidden.
--
-- Oracle equivalent:
--   Identical syntax. Oracle also supports RANK() OVER (PARTITION BY ... ORDER BY ...).
--   The only difference: Oracle requires FROM dual for standalone SELECT; not relevant here.
CREATE OR REPLACE VIEW v_top_products_by_category AS
SELECT
    category,
    id                  AS product_id,
    name                AS product_name,
    total_orders,
    total_revenue,
    category_rank
FROM (
    SELECT
        p.category,
        p.id,
        p.name,
        COUNT(o.id)                                         AS total_orders,
        COALESCE(SUM(o.amount), 0)                          AS total_revenue,
        RANK() OVER (
            PARTITION BY p.category
            ORDER BY COUNT(o.id) DESC, SUM(o.amount) DESC
        )                                                   AS category_rank
    FROM products p
    LEFT JOIN orders o
        ON o.product_id = p.id
        AND o.status = 'CONFIRMED'
    GROUP BY p.category, p.id, p.name
) ranked
WHERE category_rank <= 5;

-- ─── View 2: Monthly revenue trend with month-over-month growth ────────────────
-- LAG() looks back one row in the ordered result set to compute the prior
-- month's revenue. Dividing the delta by the prior value gives the growth rate.
-- NULLIF prevents division-by-zero when the prior month had zero revenue.
--
-- Oracle equivalent:
--   Identical. LAG() is ANSI SQL:2003 and supported by both engines.
--   Oracle date truncation: TRUNC(created_at, 'MM') instead of DATE_TRUNC('month', ...).
--   Oracle format: TO_CHAR(month_start, 'YYYY-MM') instead of TO_CHAR(..., 'YYYY-MM').
CREATE OR REPLACE VIEW v_monthly_revenue_trend AS
SELECT
    TO_CHAR(month_start, 'YYYY-MM')                         AS month,
    total_revenue,
    prev_month_revenue,
    ROUND(
        (total_revenue - COALESCE(prev_month_revenue, 0))
        / NULLIF(COALESCE(prev_month_revenue, 0), 0) * 100,
        2
    )                                                       AS growth_pct
FROM (
    SELECT
        DATE_TRUNC('month', created_at)                     AS month_start,
        SUM(amount)                                         AS total_revenue,
        LAG(SUM(amount)) OVER (
            ORDER BY DATE_TRUNC('month', created_at)
        )                                                   AS prev_month_revenue
    FROM orders
    WHERE status = 'CONFIRMED'
    GROUP BY DATE_TRUNC('month', created_at)
) monthly
ORDER BY month_start;

-- ─── View 3: Customer order summary with lifetime value ────────────────────────
-- Computes per-customer metrics used by the marketing and retention teams.
-- FIRST_VALUE / LAST_VALUE could be used instead of MIN/MAX, but MIN/MAX
-- is clearer intent when we only need the boundary values.
--
-- Oracle equivalent:
--   LISTAGG(category, ', ') WITHIN GROUP (ORDER BY category)
--   instead of STRING_AGG(DISTINCT category, ', ' ORDER BY category).
--   All other expressions are identical.
CREATE OR REPLACE VIEW v_customer_order_summary AS
SELECT
    customer_id,
    COUNT(*)                                                AS order_count,
    SUM(amount)                                             AS lifetime_value,
    AVG(amount)                                             AS avg_order_value,
    MIN(o.created_at)                                       AS first_order_at,
    MAX(o.created_at)                                       AS last_order_at,
    STRING_AGG(DISTINCT category, ', ' ORDER BY category)  AS categories_purchased
FROM orders o
JOIN products p ON p.id = o.product_id
WHERE o.status = 'CONFIRMED'
GROUP BY customer_id;

-- ─── View 4: Low-stock alert with reorder priority ────────────────────────────
-- Uses the partial index already defined in V1 (idx_products_stock WHERE stock < 10)
-- to make this query fast without a full table scan.
-- CASE expression encodes urgency tiers — consumable by the alerting system.
CREATE OR REPLACE VIEW v_low_stock_alerts AS
SELECT
    id          AS product_id,
    name,
    category,
    stock       AS current_stock,
    CASE
        WHEN stock = 0 THEN 'CRITICAL'
        WHEN stock < 5 THEN 'HIGH'
        ELSE 'MEDIUM'
    END         AS urgency
FROM products
WHERE stock < 10
ORDER BY stock ASC, category;

-- ─── Supporting indexes (only those not already created in V1/V2) ──────────────
-- Composite index to speed up the join in v_top_products_by_category.
-- Covers the common filter: status = 'CONFIRMED' AND product_id = ?.
CREATE INDEX IF NOT EXISTS idx_orders_product_status
    ON orders (product_id, status)
    WHERE status = 'CONFIRMED';

-- Supports v_monthly_revenue_trend date truncation without a sequential scan.
-- A plain btree index on created_at is sufficient: PostgreSQL can use it for
-- range scans on DATE_TRUNC('month', created_at) without a functional index.
-- Functional indexes on TIMESTAMPTZ require an IMMUTABLE expression; casts to
-- ::timestamp or AT TIME ZONE are STABLE (timezone-dependent), not IMMUTABLE.
CREATE INDEX IF NOT EXISTS idx_orders_created_month
    ON orders (created_at)
    WHERE status = 'CONFIRMED';

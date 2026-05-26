-- critical_stock.sql
-- Products with critical stock (< 10 units) that have also sold > 50 units
-- in the last 30 days. Used by the purchasing team to trigger emergency restocks.
--
-- CTE chain:
--   recent_sales    → confirmed order lines from the last 30 days
--   sales_summary   → aggregate sold quantity per product
--   critical_stock  → join with products table, filter stock < 10
-- Final SELECT: filter to products with sales_total > 50, ranked by urgency.
--
-- Why split into three CTEs instead of one large query?
--   Each CTE has a single logical purpose. A reviewer can validate each step
--   independently. The planner sees the same execution plan either way (CTEs
--   are inlined by default in PostgreSQL 12+), so there is no performance cost.
--
-- PostgreSQL vs Oracle differences:
--   PostgreSQL: NOW() - INTERVAL '30 days'
--   Oracle:     SYSDATE - 30
--   No other syntax differences.

WITH recent_sales AS (
    -- Raw order lines: confirmed sales within the rolling 30-day window.
    -- Includes quantity because an order may cover multiple units of the same product.
    SELECT
        o.product_id,
        o.quantity
    FROM  orders o
    WHERE o.status     = 'CONFIRMED'
      AND o.created_at >= NOW() - INTERVAL '30 days'
      -- Oracle: AND o.created_at >= SYSDATE - 30
),
sales_summary AS (
    -- Total units sold per product over the window.
    SELECT
        product_id,
        SUM(quantity) AS sales_total
    FROM  recent_sales
    GROUP BY product_id
),
critical_stock AS (
    -- Products whose current stock is below the critical threshold (< 10 units).
    -- The partial index idx_products_stock (WHERE stock < 10) defined in V1 makes
    -- this scan very cheap — only low-stock rows are read.
    SELECT
        p.id            AS product_id,
        p.name,
        p.category,
        p.stock         AS current_stock,
        ss.sales_total,
        -- Urgency tier derived from stock level, consumed by the alerting system.
        CASE
            WHEN p.stock = 0 THEN 'OUT_OF_STOCK'
            WHEN p.stock < 5 THEN 'CRITICAL'
            ELSE 'LOW'
        END             AS urgency
    FROM  products p
    JOIN  sales_summary ss ON ss.product_id = p.id
    WHERE p.stock < 10
)
SELECT
    product_id,
    name,
    category,
    current_stock,
    sales_total,
    urgency
FROM  critical_stock
WHERE sales_total > 50
ORDER BY
    -- Most urgent first: OUT_OF_STOCK → CRITICAL → LOW, then by highest sales within tier.
    CASE urgency
        WHEN 'OUT_OF_STOCK' THEN 1
        WHEN 'CRITICAL'     THEN 2
        ELSE                     3
    END,
    sales_total DESC;

-- V4: Rewrite analytics views with CTEs for readability and add GENERATE_SERIES
--     to the revenue trend so zero-sales months are not silently omitted.
--
-- Why CREATE OR REPLACE: V3 already created these views. This migration replaces
-- them in-place so existing Flyway history is preserved. Any query plan that was
-- cached for V3 views is invalidated when the view definition changes.
--
-- Why CTEs instead of inline subqueries (V3 style):
--   CTEs name each logical step. A reviewer can read them top-to-bottom like
--   paragraphs — "build the counts, then rank, then filter" — rather than
--   unwinding nested SELECTs from the inside out.
--   The PostgreSQL query planner materialises CTEs in the same way as subqueries
--   (as of PG 12, CTEs are inlined by default unless MATERIALIZED is specified),
--   so there is no performance penalty.
--
-- Oracle equivalents are noted inline where syntax differs.

-- ─── View 1: Top 5 best-selling products per category ─────────────────────────
--
-- CTE structure:
--   order_counts   → aggregate confirmed order totals per product
--   ranked         → apply RANK() within each category
-- Final SELECT filters to category_rank <= 5.
--
-- Oracle: RANK() OVER (...) is identical. STRING_AGG → LISTAGG.
CREATE OR REPLACE VIEW v_top_products_by_category AS
WITH order_counts AS (
    -- Aggregate order volume and revenue per product.
    -- LEFT JOIN keeps products with zero confirmed orders (total_orders = 0).
    SELECT
        p.id                                                       AS product_id,
        p.name                                                     AS product_name,
        p.category,
        COUNT(o.id)                                                AS total_orders,
        COALESCE(SUM(o.amount), 0)                                 AS total_revenue
    FROM products p
    LEFT JOIN orders o
        ON  o.product_id = p.id
        AND o.status     = 'CONFIRMED'
    GROUP BY p.id, p.name, p.category
),
ranked AS (
    -- RANK() (not ROW_NUMBER) so that ties within a category share the same rank.
    -- Two products with identical order counts are both rank 1 — neither is
    -- arbitrarily promoted at the expense of the other.
    SELECT
        product_id,
        product_name,
        category,
        total_orders,
        total_revenue,
        RANK() OVER (
            PARTITION BY category
            ORDER BY total_orders DESC, total_revenue DESC
        ) AS category_rank
    FROM order_counts
)
SELECT
    category,
    product_id,
    product_name,
    total_orders,
    total_revenue,
    category_rank
FROM ranked
WHERE category_rank <= 5;


-- ─── View 2: Monthly revenue trend with month-over-month growth ────────────────
--
-- CTE structure:
--   date_spine      → GENERATE_SERIES from first to last confirmed-order month.
--                     This ensures every calendar month appears, even those with
--                     zero sales. Without this, a reporting dashboard would show
--                     gaps and mislead users into thinking "no data" vs "no sales".
--   monthly_revenue → aggregate confirmed revenue per month.
--   with_trend      → LEFT JOIN spine onto revenue, apply LAG() for prior month.
-- Final SELECT formats and computes the growth percentage.
--
-- Oracle equivalent:
--   Replace GENERATE_SERIES with a recursive CTE or CONNECT BY LEVEL trick.
--   Example:
--     WITH date_spine (month_start) AS (
--       SELECT TRUNC(MIN(created_at), 'MM') FROM orders WHERE status = 'CONFIRMED'
--       UNION ALL
--       SELECT ADD_MONTHS(month_start, 1) FROM date_spine
--       WHERE month_start < TRUNC((SELECT MAX(created_at) FROM orders WHERE status='CONFIRMED'), 'MM')
--     )
--   TRUNC(col, 'MM') replaces DATE_TRUNC('month', col).
--   TO_CHAR(month_start, 'YYYY-MM') is identical syntax in Oracle.
CREATE OR REPLACE VIEW v_monthly_revenue_trend AS
WITH date_spine AS (
    -- GENERATE_SERIES emits one row per calendar month between the first and last
    -- confirmed order. Rows with no matching revenue LEFT JOIN to NULL → COALESCE(0).
    SELECT gs::date AS month_start
    FROM GENERATE_SERIES(
        (SELECT DATE_TRUNC('month', MIN(created_at))
         FROM   orders
         WHERE  status = 'CONFIRMED'),
        (SELECT DATE_TRUNC('month', MAX(created_at))
         FROM   orders
         WHERE  status = 'CONFIRMED'),
        INTERVAL '1 month'
    ) gs
),
monthly_revenue AS (
    -- Real revenue per calendar month from confirmed orders only.
    SELECT
        DATE_TRUNC('month', created_at) AS month_start,
        SUM(amount)                     AS total_revenue
    FROM  orders
    WHERE status = 'CONFIRMED'
    GROUP BY DATE_TRUNC('month', created_at)
),
with_trend AS (
    -- LEFT JOIN preserves zero-revenue months from the spine.
    -- LAG over the full spine means a gap month correctly shows 0 as the prior value,
    -- not the last non-zero month — avoids artificially inflating growth rates.
    SELECT
        d.month_start,
        COALESCE(m.total_revenue, 0)                                    AS total_revenue,
        LAG(COALESCE(m.total_revenue, 0))
            OVER (ORDER BY d.month_start)                               AS prev_month_revenue
    FROM date_spine d
    LEFT JOIN monthly_revenue m ON m.month_start = d.month_start
)
SELECT
    TO_CHAR(month_start, 'YYYY-MM')                                     AS month,
    total_revenue,
    prev_month_revenue,
    ROUND(
        (total_revenue - COALESCE(prev_month_revenue, 0))
        / NULLIF(COALESCE(prev_month_revenue, 0), 0) * 100,
        2
    )                                                                    AS growth_pct
FROM  with_trend
ORDER BY month_start;


-- ─── View 3: Customer order summary with lifetime value ────────────────────────
--
-- CTE structure:
--   confirmed_orders → pre-filter to confirmed status and join product category.
--                      Moving the JOIN and WHERE into a CTE keeps the final
--                      aggregation clean and lets the planner push the filter
--                      down before the group-by fan-out.
--
-- Oracle: LISTAGG(category, ', ') WITHIN GROUP (ORDER BY category)
--         replaces STRING_AGG(DISTINCT category, ', ' ORDER BY category).
CREATE OR REPLACE VIEW v_customer_order_summary AS
WITH confirmed_orders AS (
    -- Pre-join products so the aggregation below is against a single, filtered set.
    -- Isolating the filter in a CTE also makes it trivial to add further filters
    -- (e.g. date range) without touching the aggregation logic.
    SELECT
        o.customer_id,
        o.amount,
        o.created_at,
        p.category
    FROM  orders o
    JOIN  products p ON p.id = o.product_id
    WHERE o.status = 'CONFIRMED'
)
SELECT
    customer_id,
    COUNT(*)                                                AS order_count,
    SUM(amount)                                             AS lifetime_value,
    AVG(amount)                                             AS avg_order_value,
    MIN(created_at)                                         AS first_order_at,
    MAX(created_at)                                         AS last_order_at,
    STRING_AGG(DISTINCT category, ', ' ORDER BY category)  AS categories_purchased
FROM confirmed_orders
GROUP BY customer_id;


-- ─── View 4: Low-stock alert with reorder priority ────────────────────────────
-- No structural change from V3; CTE would add no clarity for a single-table scan.
-- Reproduced here so all four views in this file are at the same revision.
CREATE OR REPLACE VIEW v_low_stock_alerts AS
WITH stock_classified AS (
    SELECT
        id      AS product_id,
        name,
        category,
        stock   AS current_stock,
        CASE
            WHEN stock = 0 THEN 'CRITICAL'
            WHEN stock < 5 THEN 'HIGH'
            ELSE 'MEDIUM'
        END     AS urgency
    FROM products
    WHERE stock < 10
)
SELECT *
FROM stock_classified
ORDER BY current_stock ASC, category;

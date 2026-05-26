-- daily_sales_report.sql
-- Daily sales for the last 7 days, broken down by category.
-- Days with zero sales for a category appear as 0 (not NULL, not missing row).
--
-- CTE chain:
--   date_series         → one row per day for the last 7 days (GENERATE_SERIES)
--   categories          → distinct product categories from the catalog
--   date_category_grid  → CROSS JOIN: every (day, category) combination
--   daily_sales         → actual confirmed sales grouped by day and category
-- Final SELECT: LEFT JOIN grid onto sales; COALESCE(sales, 0) fills gaps.
--
-- Why CROSS JOIN + LEFT JOIN instead of GROUP BY alone?
--   A plain GROUP BY only produces rows where at least one sale occurred.
--   On a day where ELECTRONICS had zero sales, that (day, ELECTRONICS) pair
--   simply disappears from the result. Business reports need the zero explicitly
--   so trend lines don't skip days and charts don't mislead.
--
-- PostgreSQL vs Oracle differences:
--   PostgreSQL: GENERATE_SERIES(start, stop, step)
--   Oracle equivalent (recursive CTE with CONNECT BY):
--
--     dates (day) AS (
--       SELECT TRUNC(SYSDATE) - 6 FROM dual
--       UNION ALL
--       SELECT day + 1 FROM dates WHERE day < TRUNC(SYSDATE)
--     )
--
--   All other syntax (WITH, CROSS JOIN, LEFT JOIN, COALESCE, DATE_TRUNC) is
--   ANSI SQL and runs on both engines.
--   Oracle: TRUNC(created_at) instead of DATE_TRUNC('day', created_at).

WITH date_series AS (
    -- GENERATE_SERIES emits one timestamp per day for the last 7 days.
    -- ::date cast strips the time component so the join key is a plain date.
    SELECT gs::date AS sale_day
    FROM GENERATE_SERIES(
        CURRENT_DATE - INTERVAL '6 days',  -- 6 days ago (inclusive) ...
        CURRENT_DATE,                       -- ... through today
        INTERVAL '1 day'
    ) gs
    -- Oracle equivalent:
    -- SELECT TRUNC(SYSDATE) - (7 - LEVEL) AS sale_day
    -- FROM dual
    -- CONNECT BY LEVEL <= 7
),
categories AS (
    -- All distinct categories currently in the product catalog.
    -- Using DISTINCT on the products table (not on orders) means a category
    -- appears even if it had zero sales in the entire 7-day window.
    SELECT DISTINCT category
    FROM  products
),
date_category_grid AS (
    -- Every (day, category) pair that SHOULD appear in the report.
    -- CROSS JOIN: 7 days × N categories = 7N rows. For N = 10 categories
    -- that is 70 rows — small enough that materialisation is free.
    SELECT
        d.sale_day,
        c.category
    FROM  date_series d
    CROSS JOIN categories c
),
daily_sales AS (
    -- Actual confirmed sales, grouped by day and category.
    -- Only rows with at least one sale appear here (no zeros yet).
    SELECT
        DATE_TRUNC('day', o.created_at)::date  AS sale_day,
        -- Oracle: TRUNC(o.created_at)         AS sale_day,
        p.category,
        SUM(o.amount)                          AS revenue,
        SUM(o.quantity)                        AS units_sold,
        COUNT(o.id)                            AS order_count
    FROM  orders o
    JOIN  products p ON p.id = o.product_id
    WHERE o.status     = 'CONFIRMED'
      AND o.created_at >= CURRENT_DATE - INTERVAL '6 days'
    GROUP BY DATE_TRUNC('day', o.created_at)::date, p.category
    -- Oracle: GROUP BY TRUNC(o.created_at), p.category
)
SELECT
    g.sale_day,
    g.category,
    COALESCE(ds.revenue,     0) AS revenue,      -- 0 when no sales that day
    COALESCE(ds.units_sold,  0) AS units_sold,
    COALESCE(ds.order_count, 0) AS order_count
FROM  date_category_grid g
LEFT JOIN daily_sales ds
       ON ds.sale_day = g.sale_day
      AND ds.category = g.category
ORDER BY
    g.sale_day   ASC,
    g.category   ASC;

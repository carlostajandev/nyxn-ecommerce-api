-- Oracle SQL equivalents for the analytics queries defined in V3__analytics_views.sql.
--
-- This file is documentation only — it does not run against the PostgreSQL instance.
-- It demonstrates the syntax differences between PostgreSQL 16 and Oracle 19c for
-- the same business queries, showing awareness of both dialects.
--
-- Summary of differences:
--   | Feature                   | PostgreSQL                        | Oracle                            |
--   |---------------------------|-----------------------------------|-----------------------------------|
--   | Date truncation           | DATE_TRUNC('month', col)          | TRUNC(col, 'MM')                  |
--   | String aggregation        | STRING_AGG(col, sep ORDER BY col) | LISTAGG(col, sep) WITHIN GROUP    |
--   | View creation/replacement | CREATE OR REPLACE VIEW            | CREATE OR REPLACE VIEW (same)     |
--   | Boolean literals          | TRUE / FALSE                      | 1 / 0 (no native boolean)         |
--   | LIMIT / OFFSET            | LIMIT n OFFSET m                  | FETCH FIRST n ROWS ONLY / ROWNUM  |
--   | Sequence / Auto-increment | SERIAL / GENERATED ALWAYS AS IDENTITY | SEQUENCE + trigger or IDENTITY |
--   | String concatenation      | || or CONCAT()                    | || or CONCAT() (same)             |
--   | ILIKE (case-insensitive)  | ILIKE                             | UPPER(col) LIKE UPPER(pattern)    |

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Top 5 best-selling products per category (Oracle version)
-- ─────────────────────────────────────────────────────────────────────────────
-- Differences from PostgreSQL:
--   - COALESCE works identically; NVL is Oracle-specific but COALESCE is preferred.
--   - Window functions (RANK, PARTITION BY, ORDER BY inside OVER) are identical.
--   - No CREATE OR REPLACE VIEW in older Oracle; use CREATE VIEW with DROP first.
--     Oracle 11g+ supports CREATE OR REPLACE VIEW.

CREATE OR REPLACE VIEW v_top_products_by_category AS
SELECT
    category,
    product_id,
    product_name,
    total_orders,
    total_revenue,
    category_rank
FROM (
    SELECT
        p.category,
        p.id                                                AS product_id,
        p.name                                              AS product_name,
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
)
WHERE category_rank <= 5;
-- Oracle note: subquery alias not required in Oracle but allowed.

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Monthly revenue trend (Oracle version)
-- ─────────────────────────────────────────────────────────────────────────────
-- PostgreSQL: DATE_TRUNC('month', created_at)
-- Oracle:     TRUNC(created_at, 'MM')
--
-- PostgreSQL: TO_CHAR(col, 'YYYY-MM')
-- Oracle:     TO_CHAR(col, 'YYYY-MM')  ← identical in this case

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
        TRUNC(created_at, 'MM')                             AS month_start,   -- Oracle syntax
        SUM(amount)                                         AS total_revenue,
        LAG(SUM(amount)) OVER (
            ORDER BY TRUNC(created_at, 'MM')
        )                                                   AS prev_month_revenue
    FROM orders
    WHERE status = 'CONFIRMED'
    GROUP BY TRUNC(created_at, 'MM')
)
ORDER BY month_start;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Customer order summary (Oracle version)
-- ─────────────────────────────────────────────────────────────────────────────
-- PostgreSQL: STRING_AGG(DISTINCT category, ', ' ORDER BY category)
-- Oracle:     LISTAGG(category, ', ') WITHIN GROUP (ORDER BY category)
--
-- Oracle's LISTAGG does not natively support DISTINCT until Oracle 19c.
-- Pre-19c workaround: wrap in a subquery to pre-deduplicate categories.

CREATE OR REPLACE VIEW v_customer_order_summary AS
SELECT
    o.customer_id,
    COUNT(*)                                                AS order_count,
    SUM(o.amount)                                           AS lifetime_value,
    AVG(o.amount)                                           AS avg_order_value,
    MIN(o.created_at)                                       AS first_order_at,
    MAX(o.created_at)                                       AS last_order_at,
    LISTAGG(DISTINCT p.category, ', ')
        WITHIN GROUP (ORDER BY p.category)                  AS categories_purchased  -- Oracle 19c+
FROM orders o
JOIN products p ON p.id = o.product_id
WHERE o.status = 'CONFIRMED'
GROUP BY o.customer_id;

-- Pre-Oracle-19c workaround for DISTINCT in LISTAGG:
-- SELECT
--     customer_id,
--     LISTAGG(category, ', ') WITHIN GROUP (ORDER BY category) AS categories_purchased
-- FROM (
--     SELECT DISTINCT o.customer_id, p.category
--     FROM orders o JOIN products p ON p.id = o.product_id
--     WHERE o.status = 'CONFIRMED'
-- )
-- GROUP BY customer_id;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. Low-stock alert (Oracle version)
-- ─────────────────────────────────────────────────────────────────────────────
-- Identical to PostgreSQL for this query — CASE expressions and ORDER BY are ANSI SQL.
-- Partial indexes (WHERE stock < 10) are NOT supported in Oracle; use a function-based
-- index or rely on Oracle's optimizer to use a standard index with range scan.

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

-- Oracle index (no partial index support):
-- CREATE INDEX idx_products_stock ON products (stock);
-- The query planner will apply a range scan for WHERE stock < 10.

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. Pagination — PostgreSQL LIMIT/OFFSET vs Oracle FETCH FIRST / ROWNUM
-- ─────────────────────────────────────────────────────────────────────────────

-- PostgreSQL:
-- SELECT * FROM products ORDER BY created_at DESC LIMIT 20 OFFSET 40;

-- Oracle 12c+ (ANSI SQL:2008 FETCH FIRST — preferred):
-- SELECT * FROM products ORDER BY created_at DESC
-- OFFSET 40 ROWS FETCH NEXT 20 ROWS ONLY;

-- Oracle pre-12c (ROWNUM workaround):
-- SELECT * FROM (
--     SELECT p.*, ROWNUM rn FROM (
--         SELECT * FROM products ORDER BY created_at DESC
--     ) p
--     WHERE ROWNUM <= 60   -- page_size * (page_number + 1)
-- )
-- WHERE rn > 40;           -- page_size * page_number

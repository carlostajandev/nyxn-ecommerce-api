-- top_customers.sql
-- Top 5 customers by total spend in the last 30 days.
--
-- CTE chain (read top-to-bottom):
--   filtered_orders  → restrict to confirmed orders within the rolling window
--   customer_stats   → aggregate spend, order count, avg ticket per customer
--   ranked_customers → apply RANK() so ties share the same position
-- Final SELECT: emit only rank <= 5.
--
-- Why RANK() and not ROW_NUMBER()?
--   Two customers with identical spend are equally "top". ROW_NUMBER() would
--   arbitrarily drop one of them. RANK() keeps both at the same position and
--   skips the next rank — consistent with business expectation of a tie.
--
-- PostgreSQL vs Oracle differences:
--   PostgreSQL: NOW() - INTERVAL '30 days'
--   Oracle:     SYSDATE - 30  (or TRUNC(SYSDATE) - 30 for day precision)
--   Everything else (WITH, RANK, PARTITION BY, OVER) is identical ANSI SQL.

WITH filtered_orders AS (
    -- Confirmed orders placed within the last 30 calendar days.
    -- Filtering here (not in the outer SELECT) lets the planner push the
    -- predicate into the index scan on (status, created_at).
    SELECT
        customer_id,
        amount
    FROM  orders
    WHERE status     = 'CONFIRMED'
      AND created_at >= NOW() - INTERVAL '30 days'
      -- Oracle equivalent:
      -- AND created_at >= SYSDATE - 30
),
customer_stats AS (
    -- One row per customer: lifetime totals within the window.
    SELECT
        customer_id,
        SUM(amount)   AS total_spent,
        COUNT(*)      AS order_count,
        AVG(amount)   AS avg_ticket
    FROM  filtered_orders
    GROUP BY customer_id
),
ranked_customers AS (
    -- RANK() within a single partition (all customers) ordered by total spend.
    -- Dense ranking is not used here: if rank 1 is tied by two customers,
    -- the next distinct rank is 3 (RANK) not 2 (DENSE_RANK) — intentional,
    -- as the business asks for the top 5 positions, not the top 5 rows.
    SELECT
        customer_id,
        total_spent,
        order_count,
        avg_ticket,
        RANK() OVER (ORDER BY total_spent DESC) AS spend_rank
    FROM  customer_stats
)
SELECT
    customer_id,
    total_spent,
    order_count,
    ROUND(avg_ticket, 2) AS avg_ticket,
    spend_rank
FROM  ranked_customers
WHERE spend_rank <= 5
ORDER BY spend_rank;

package com.nyxn.ecommerce.infrastructure.analytics;

import com.nyxn.ecommerce.domain.ports.in.analytics.ProductAnalyticsUseCase;
import com.nyxn.ecommerce.infrastructure.config.RedisConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPQL/native query adapter for the analytics views defined in V3.
 *
 * <p>Why native SQL instead of JPQL for these queries:
 *
 * <ul>
 *   <li>JPQL operates on entity graphs — it cannot query views directly.
 *   <li>Window functions (RANK, LAG) are not part of the JPQL spec; they are database-specific.
 *   <li>The queries return flat projections (not entity trees), so the overhead of hydrating JPA
 *       entities would be wasted.
 * </ul>
 *
 * <p>Why {@code @Repository} (not {@code @Component}): this class performs database access; the
 * {@code @Repository} stereotype triggers Spring's persistence exception translation, converting
 * vendor-specific SQL exceptions into Spring's {@code DataAccessException} hierarchy.
 *
 * <p>Caching: all three queries are cached with a 15-minute TTL. Analytics data is acceptable to
 * serve slightly stale — a 15-minute lag in the top-products ranking is not a business problem. The
 * cache key includes the method name; no parameters, so a single entry covers all calls.
 */
@Repository
@Transactional(readOnly = true)
public class JpaProductAnalyticsAdapter implements ProductAnalyticsUseCase {

  @PersistenceContext private EntityManager em;

  @Override
  @Cacheable(value = RedisConfig.CACHE_ANALYTICS_TOP, unless = "#result.isEmpty()")
  public List<TopProductDto> getTopProductsByCategory() {
    // The view already filters to rank <= 5; this query just reads all rows from it.
    // Native SQL because the view uses a window function subquery not expressible in JPQL.
    @SuppressWarnings("unchecked")
    List<Object[]> rows =
        em.createNativeQuery(
                """
                SELECT category, product_id::text, product_name,
                       total_orders, total_revenue, category_rank
                FROM v_top_products_by_category
                ORDER BY category, category_rank
                """)
            .getResultList();

    return rows.stream()
        .map(
            r ->
                new TopProductDto(
                    (String) r[0],
                    (String) r[1],
                    (String) r[2],
                    ((Number) r[3]).longValue(),
                    (BigDecimal) r[4],
                    ((Number) r[5]).longValue()))
        .toList();
  }

  @Override
  @Cacheable(value = RedisConfig.CACHE_ANALYTICS_REVENUE, unless = "#result.isEmpty()")
  public List<MonthlyRevenueDto> getMonthlyRevenueTrend() {
    @SuppressWarnings("unchecked")
    List<Object[]> rows =
        em.createNativeQuery(
                """
                SELECT month, total_revenue, COALESCE(growth_pct, 0)
                FROM v_monthly_revenue_trend
                ORDER BY month
                """)
            .getResultList();

    return rows.stream()
        .map(r -> new MonthlyRevenueDto((String) r[0], (BigDecimal) r[1], (BigDecimal) r[2]))
        .toList();
  }

  @Override
  @Cacheable(value = RedisConfig.CACHE_ANALYTICS_LOW_STOCK, unless = "#result.isEmpty()")
  public List<LowStockAlertDto> getLowStockAlerts() {
    @SuppressWarnings("unchecked")
    List<Object[]> rows =
        em.createNativeQuery(
                """
                SELECT product_id::text, name, category, current_stock, urgency
                FROM v_low_stock_alerts
                """)
            .getResultList();

    return rows.stream()
        .map(
            r ->
                new LowStockAlertDto(
                    (String) r[0],
                    (String) r[1],
                    (String) r[2],
                    ((Number) r[3]).intValue(),
                    (String) r[4]))
        .toList();
  }
}

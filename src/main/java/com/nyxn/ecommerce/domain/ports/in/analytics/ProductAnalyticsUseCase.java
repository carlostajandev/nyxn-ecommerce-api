package com.nyxn.ecommerce.domain.ports.in.analytics;

import java.math.BigDecimal;
import java.util.List;

/**
 * Inbound port: product analytics and reporting queries.
 *
 * <p>Analytics queries are separated from CRUD use cases because they have different
 * characteristics: they are read-only, potentially expensive, backed by views rather than entity
 * graphs, and cached aggressively. Placing them in a dedicated port makes the read/write split
 * visible at the architecture level and satisfies ISP — a reporting dashboard controller only
 * depends on this interface.
 */
public interface ProductAnalyticsUseCase {

  /**
   * Returns the top products per category ranked by confirmed order count, then by revenue.
   *
   * <p>Backed by {@code v_top_products_by_category} (V3 migration). Uses PostgreSQL RANK() window
   * function so tied products at the same rank are both included — no arbitrary tie-breaking.
   *
   * @return up to 5 results per category, ranked descending by sales volume
   */
  List<TopProductDto> getTopProductsByCategory();

  /**
   * Returns month-over-month revenue with growth percentage.
   *
   * <p>Backed by {@code v_monthly_revenue_trend}. Uses LAG() to compare each month with the
   * previous one without a self-join.
   *
   * @return one row per calendar month with revenue and growth rate
   */
  List<MonthlyRevenueDto> getMonthlyRevenueTrend();

  /**
   * Returns products with stock below the low-stock threshold (currently 10 units).
   *
   * <p>Backed by {@code v_low_stock_alerts} which uses the partial index {@code idx_products_stock}
   * (V1) to avoid full table scans. Results include urgency tier (CRITICAL / HIGH / MEDIUM) for
   * prioritisation.
   *
   * @return products ordered by remaining stock ascending
   */
  List<LowStockAlertDto> getLowStockAlerts();

  // ─── Nested DTO records ────────────────────────────────────────────────────
  // Records are used here instead of separate files to keep the port self-contained.
  // Each record maps directly to one row of the corresponding view.

  record TopProductDto(
      String category,
      String productId,
      String productName,
      long totalOrders,
      BigDecimal totalRevenue,
      long categoryRank) {}

  record MonthlyRevenueDto(String month, BigDecimal totalRevenue, BigDecimal growthPct) {}

  record LowStockAlertDto(
      String productId, String name, String category, int currentStock, String urgency) {}
}

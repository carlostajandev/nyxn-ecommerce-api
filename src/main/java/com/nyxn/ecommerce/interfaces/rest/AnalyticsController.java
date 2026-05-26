package com.nyxn.ecommerce.interfaces.rest;

import com.nyxn.ecommerce.domain.ports.in.analytics.ProductAnalyticsUseCase;
import com.nyxn.ecommerce.domain.ports.in.analytics.ProductAnalyticsUseCase.LowStockAlertDto;
import com.nyxn.ecommerce.domain.ports.in.analytics.ProductAnalyticsUseCase.MonthlyRevenueDto;
import com.nyxn.ecommerce.domain.ports.in.analytics.ProductAnalyticsUseCase.TopProductDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for analytics and reporting endpoints.
 *
 * <p>All responses are served from the Redis cache with a 15-minute TTL (configured in {@link
 * com.nyxn.ecommerce.infrastructure.config.RedisConfig}). On a cache miss the query hits PostgreSQL
 * views backed by window functions — acceptable latency for infrequent dashboard calls.
 *
 * <p>These endpoints are intentionally read-only and have no side effects. In a production system
 * they would be behind a separate rate limiter and potentially served from a read replica.
 */
@RestController
@RequestMapping("/api/v1/analytics")
@Tag(
    name = "Analytics",
    description = "Product and revenue analytics powered by SQL window functions")
public class AnalyticsController {

  private final ProductAnalyticsUseCase analyticsUseCase;

  public AnalyticsController(ProductAnalyticsUseCase analyticsUseCase) {
    this.analyticsUseCase = analyticsUseCase;
  }

  @Operation(
      summary = "Top products by category",
      description =
          "Returns the top 5 products per category by confirmed order count. "
              + "Uses PostgreSQL RANK() window function. Cached for 15 minutes.")
  @ApiResponse(responseCode = "200", description = "Ranked products per category")
  @GetMapping("/top-products")
  public ResponseEntity<List<TopProductDto>> topProducts() {
    return ResponseEntity.ok(analyticsUseCase.getTopProductsByCategory());
  }

  @Operation(
      summary = "Monthly revenue trend",
      description =
          "Month-over-month revenue with growth percentage. "
              + "Uses PostgreSQL LAG() window function. Cached for 15 minutes.")
  @ApiResponse(responseCode = "200", description = "Monthly revenue trend")
  @GetMapping("/revenue-trend")
  public ResponseEntity<List<MonthlyRevenueDto>> revenueTrend() {
    return ResponseEntity.ok(analyticsUseCase.getMonthlyRevenueTrend());
  }

  @Operation(
      summary = "Low-stock alerts",
      description =
          "Products with stock below 10 units, classified by urgency (CRITICAL/HIGH/MEDIUM). "
              + "Uses the partial index idx_products_stock. Cached for 15 minutes.")
  @ApiResponse(responseCode = "200", description = "Low-stock product list")
  @GetMapping("/low-stock")
  public ResponseEntity<List<LowStockAlertDto>> lowStock() {
    return ResponseEntity.ok(analyticsUseCase.getLowStockAlerts());
  }
}

package com.nyxn.ecommerce.interfaces.rest;

import com.nyxn.ecommerce.domain.ports.in.analytics.ProductAnalyticsUseCase;
import com.nyxn.ecommerce.domain.ports.in.analytics.ProductAnalyticsUseCase.LowStockAlertDto;
import com.nyxn.ecommerce.domain.ports.in.analytics.ProductAnalyticsUseCase.MonthlyRevenueDto;
import com.nyxn.ecommerce.domain.ports.in.analytics.ProductAnalyticsUseCase.TopProductDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
          "Returns the top 5 products per category ranked by confirmed order count. "
              + "Ties share the same rank (RANK, not ROW_NUMBER). "
              + "Result is served from Redis (TTL 15 min ± 90 s jitter). "
              + "Cache miss triggers a PostgreSQL window function query over the v_top_products_by_category view.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Ranked products per category"),
    @ApiResponse(
        responseCode = "500",
        description = "Unexpected error querying PostgreSQL or deserialising the cache",
        content = @Content(schema = @Schema(implementation = Void.class))),
    @ApiResponse(
        responseCode = "503",
        description =
            "Redis unavailable. If spring.cache.redis.fallback-to-no-op=false this surfaces as 503 "
                + "— configure the cache to fall through to the DB on Redis outage.",
        content = @Content(schema = @Schema(implementation = Void.class)))
  })
  @GetMapping("/top-products")
  public ResponseEntity<List<TopProductDto>> topProducts() {
    return ResponseEntity.ok(analyticsUseCase.getTopProductsByCategory());
  }

  @Operation(
      summary = "Monthly revenue trend",
      description =
          "Month-over-month revenue growth rate for confirmed orders. "
              + "Uses PostgreSQL LAG() window function and GENERATE_SERIES to include zero-revenue months. "
              + "Cached for 15 minutes (± 90 s jitter).")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Monthly revenue trend"),
    @ApiResponse(
        responseCode = "500",
        description = "Unexpected error querying the revenue trend view",
        content = @Content(schema = @Schema(implementation = Void.class))),
    @ApiResponse(
        responseCode = "503",
        description = "Redis cache unavailable",
        content = @Content(schema = @Schema(implementation = Void.class)))
  })
  @GetMapping("/revenue-trend")
  public ResponseEntity<List<MonthlyRevenueDto>> revenueTrend() {
    return ResponseEntity.ok(analyticsUseCase.getMonthlyRevenueTrend());
  }

  @Operation(
      summary = "Low-stock alerts",
      description =
          "Products with stock < 10 units, classified by urgency tier: "
              + "CRITICAL (stock = 0), HIGH (< 5), MEDIUM (5–9). "
              + "Backed by the partial index idx_products_stock (WHERE stock < 10) for O(low-stock rows) scan. "
              + "Cached for 15 minutes; evicted immediately on any stock reserve or release operation.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Low-stock product list"),
    @ApiResponse(
        responseCode = "500",
        description = "Unexpected error querying the low-stock view",
        content = @Content(schema = @Schema(implementation = Void.class))),
    @ApiResponse(
        responseCode = "503",
        description = "Redis cache unavailable",
        content = @Content(schema = @Schema(implementation = Void.class)))
  })
  @GetMapping("/low-stock")
  public ResponseEntity<List<LowStockAlertDto>> lowStock() {
    return ResponseEntity.ok(analyticsUseCase.getLowStockAlerts());
  }
}

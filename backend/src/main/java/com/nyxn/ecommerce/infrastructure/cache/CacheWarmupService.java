package com.nyxn.ecommerce.infrastructure.cache;

import com.nyxn.ecommerce.domain.ports.in.analytics.ProductAnalyticsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * Pre-populates the analytics caches immediately after the application is ready to serve traffic.
 *
 * <h2>Why warm on startup?</h2>
 *
 * <p>Analytics queries hit PostgreSQL views backed by window functions — acceptable for infrequent
 * dashboard calls, but noticeably slow (tens of milliseconds) on a cold cache under high load.
 * During a Cyber Day event, the first request after application startup or after a cache flush
 * would take the full DB-round-trip path. Warming at startup converts that first-request latency
 * spike into a predictable, silent background operation before the load ramp begins.
 *
 * <h2>Why {@link ApplicationReadyEvent}?</h2>
 *
 * <p>{@code ApplicationReadyEvent} fires after the entire Spring context is initialized, all Flyway
 * migrations have run, and the embedded server is accepting connections. Using {@code
 * ContextRefreshedEvent} instead would fire too early — before Flyway runs and before the
 * datasource pool is fully configured, causing the warmup to fail with a misleading error.
 *
 * <h2>Failure handling</h2>
 *
 * <p>Cache warming is best-effort. If the DB is temporarily unavailable at startup (e.g., rolling
 * deploy with a short DB restart), the warmup failure must not prevent the application from
 * starting. The first real request will trigger a cache miss and populate the cache normally. The
 * {@link Exception} catch is intentionally broad — any SQL error, connection failure, or
 * serialisation problem is equally non-fatal at this stage.
 */
@Component
public class CacheWarmupService implements ApplicationListener<ApplicationReadyEvent> {

  private static final Logger log = LoggerFactory.getLogger(CacheWarmupService.class);

  // Inject the use-case interface, not the concrete adapter, so this service
  // remains testable and decoupled from the JPA implementation.
  private final ProductAnalyticsUseCase analyticsUseCase;

  public CacheWarmupService(ProductAnalyticsUseCase analyticsUseCase) {
    this.analyticsUseCase = analyticsUseCase;
  }

  @Override
  public void onApplicationEvent(ApplicationReadyEvent event) {
    log.info("Starting analytics cache warmup...");
    long startMs = System.currentTimeMillis();

    try {
      // Each call checks the cache first (via @Cacheable on the adapter). On a fresh start
      // all three will miss and execute against the DB, populating the cache. On a restart
      // after a crash, existing Redis entries are served from cache — warmup is a no-op.
      analyticsUseCase.getTopProductsByCategory();
      analyticsUseCase.getMonthlyRevenueTrend();
      analyticsUseCase.getLowStockAlerts();

      log.info("Analytics cache warmup complete in {} ms", System.currentTimeMillis() - startMs);
    } catch (Exception e) {
      // Non-fatal: log a warning and continue. The application starts without warm caches;
      // the first real request to each endpoint will populate the cache on demand.
      log.warn(
          "Analytics cache warmup failed after {} ms — caches will be populated on first request",
          System.currentTimeMillis() - startMs,
          e);
    }
  }
}

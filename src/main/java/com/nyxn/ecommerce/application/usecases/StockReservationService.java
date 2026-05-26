package com.nyxn.ecommerce.application.usecases;

import com.nyxn.ecommerce.domain.exceptions.InsufficientStockException;
import com.nyxn.ecommerce.domain.ports.in.ReserveStockUseCase;
import com.nyxn.ecommerce.domain.ports.out.StockCachePort;
import com.nyxn.ecommerce.domain.valueobject.ProductId;
import com.nyxn.ecommerce.infrastructure.stock.ProductStockUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * High-throughput stock reservation service for peak-traffic scenarios.
 *
 * <h2>Two-layer reservation flow</h2>
 *
 * <pre>
 *  Caller
 *    │
 *    ▼
 *  [Redis — Lua DECREMENT_IF_SUFFICIENT]          ← single atomic operation, O(1)
 *    │  cache miss → skip to DB directly
 *    │  insufficient stock → reject immediately, no DB touched
 *    │  success → proceed
 *    ▼
 *  [PostgreSQL — versioned UPDATE with retry]     ← 1–3 attempts, exponential backoff + jitter
 *    │  success → done
 *    │  conflict after all retries → compensate Redis, throw StockConflictException
 *    │  insufficient stock from DB → compensate Redis, throw InsufficientStockException
 *    ▼
 *  Reservation committed
 * </pre>
 *
 * <h2>Why Redis first?</h2>
 *
 * <p>During a flash sale, a product with 100 units attracts thousands of concurrent requests.
 * Without the Redis gate, all of them reach PostgreSQL simultaneously. The optimistic-lock loop
 * serialises them one at a time — O(n) round trips to the DB for n requests. With Redis, the first
 * 100 requests decrement the counter to 0 and are allowed through; requests 101-N are rejected
 * instantly at Redis without generating a single DB connection. The DB sees at most 100 concurrent
 * writers instead of 1000+.
 *
 * <h2>Consistency guarantee</h2>
 *
 * <p>The Redis decrement happens before the DB commit. If the DB write fails after all retries, the
 * Redis decrement is reversed (compensating increment). This means the system is at-most-once at
 * the DB layer: it is possible for a request to succeed in Redis and then fail in the DB, leaving
 * the stock temporarily under-counted in Redis until the compensation runs. The DB remains the
 * source of truth; Redis drifts only within a single request's failure window.
 */
@Service
public class StockReservationService implements ReserveStockUseCase {

  private static final Logger log = LoggerFactory.getLogger(StockReservationService.class);

  // Redis-backed cache port: atomic Lua-script operations
  private final StockCachePort stockCachePort;

  // Separate @Component so @Retryable + @Transactional proxy composition works correctly.
  // See ProductStockUpdater Javadoc for the detailed proxy-ordering explanation.
  private final ProductStockUpdater productStockUpdater;

  public StockReservationService(
      StockCachePort stockCachePort, ProductStockUpdater productStockUpdater) {
    this.stockCachePort = stockCachePort;
    this.productStockUpdater = productStockUpdater;
  }

  @Override
  public void reserve(ProductId productId, int quantity) {
    if (quantity <= 0) {
      throw new IllegalArgumentException("Reservation quantity must be positive, got: " + quantity);
    }

    boolean cacheReserved = tryReserveInCache(productId, quantity);

    try {
      // DB write with optimistic-lock retry. If this throws, we compensate the cache below.
      productStockUpdater.decreaseStock(productId, quantity);
      log.info("Stock reserved: product={} qty={} cacheHit={}", productId, quantity, cacheReserved);
    } catch (Exception e) {
      // The DB write failed (either InsufficientStockException or StockConflictException after
      // retries). The DB was not updated; undo the Redis decrement to keep the layers consistent.
      if (cacheReserved) {
        log.warn("DB reservation failed for {}; compensating Redis decrement", productId);
        stockCachePort.increment(productId, quantity);
      }
      throw e;
    }
  }

  @Override
  public void release(ProductId productId, int quantity) {
    if (quantity <= 0) {
      throw new IllegalArgumentException("Release quantity must be positive, got: " + quantity);
    }
    stockCachePort.increment(productId, quantity);
    productStockUpdater.increaseStock(productId, quantity);
    log.info("Stock released: product={} qty={}", productId, quantity);
  }

  /**
   * Attempts the Redis fast path.
   *
   * <p>Returns {@code true} if the cache decremented successfully (caller is responsible for
   * compensating on DB failure). Returns {@code false} if the key was absent (cache miss) — in that
   * case the DB path proceeds without a cache reservation to undo on failure.
   *
   * <p>Throws {@link InsufficientStockException} immediately on cache hit with insufficient stock —
   * no point hitting the DB if Redis already knows there's no stock.
   */
  private boolean tryReserveInCache(ProductId productId, int quantity) {
    long result = stockCachePort.tryDecrement(productId, quantity);

    if (result == -1L) {
      // Cache miss: Redis has no entry for this product. The DB path proceeds as the sole
      // mechanism. This happens on first access after a Redis restart or for newly created
      // products whose stock was not yet synced. Acceptable — performance degrades to DB-only
      // rather than failing entirely.
      log.debug("Cache miss for product {}; proceeding with DB-only path", productId);
      return false;
    }

    if (result == -2L) {
      // Redis confirmed insufficient stock. Reject without touching the DB.
      throw new InsufficientStockException(
          "Insufficient stock for product " + productId + " (quantity: " + quantity + ")");
    }

    return true;
  }
}

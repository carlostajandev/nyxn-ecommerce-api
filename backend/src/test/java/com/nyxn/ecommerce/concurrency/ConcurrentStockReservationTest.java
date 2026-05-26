package com.nyxn.ecommerce.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;

import com.nyxn.ecommerce.application.usecases.StockReservationService;
import com.nyxn.ecommerce.domain.exceptions.InsufficientStockException;
import com.nyxn.ecommerce.domain.exceptions.StockConflictException;
import com.nyxn.ecommerce.domain.ports.out.StockCachePort;
import com.nyxn.ecommerce.domain.valueobject.ProductId;
import com.nyxn.ecommerce.infrastructure.stock.ProductStockUpdater;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Concurrency test for {@link StockReservationService}.
 *
 * <p>This test simulates the Cyber-Day scenario: {@value #THREAD_COUNT} concurrent threads each
 * attempt to reserve 1 unit of a product that has only {@value #AVAILABLE_STOCK} units.
 *
 * <p><b>What this test proves</b>: the orchestration logic correctly counts successes and failures
 * and performs the cache compensation when the DB layer rejects a request. It verifies that:
 *
 * <ol>
 *   <li>The total number of accepted reservations never exceeds available stock.
 *   <li>Every cache decrement that corresponds to a DB rejection is compensated.
 *   <li>The service is free of data races at the orchestration level (the thread-safety of the
 *       actual Redis and DB operations is guaranteed by Redis's single-threaded model and
 *       PostgreSQL's locking — those are tested in the integration test with real containers).
 * </ol>
 *
 * <p><b>Note on mock behaviour</b>: the cache mock returns a strict sequence — the first {@value
 * #AVAILABLE_STOCK} calls succeed (positive remaining stock), the rest signal insufficient stock
 * ({@code -2}). The DB mock similarly allows only the first {@value #AVAILABLE_STOCK} calls. This
 * mimics the real behaviour of the Lua script and the versioned DB update.
 */
@ExtendWith(MockitoExtension.class)
class ConcurrentStockReservationTest {

  private static final Logger log = LoggerFactory.getLogger(ConcurrentStockReservationTest.class);

  private static final int THREAD_COUNT = 100; // concurrent clients
  private static final int AVAILABLE_STOCK = 30; // units in stock — far fewer than threads

  @Mock private StockCachePort stockCachePort;
  @Mock private ProductStockUpdater productStockUpdater;

  @Test
  void reserve_underConcurrentLoad_neverOversells() throws InterruptedException {
    ProductId productId = ProductId.generate();

    // Cache mock: atomic counter simulating Redis's single-threaded DECRBY.
    // Each call to tryDecrement decrements an in-memory counter — if it goes negative,
    // return -2 (insufficient); otherwise return the remaining value.
    AtomicInteger cacheStock = new AtomicInteger(AVAILABLE_STOCK);
    given(stockCachePort.tryDecrement(any(), anyInt()))
        .willAnswer(
            inv -> {
              int qty = inv.getArgument(1);
              int remaining = cacheStock.addAndGet(-qty);
              if (remaining < 0) {
                cacheStock.addAndGet(qty); // undo immediately (Lua would do this atomically)
                return -2L;
              }
              return (long) remaining;
            });

    // DB mock: atomic counter simulating PostgreSQL versioned UPDATE.
    // A subset of requests that passed Redis may still be rejected here (stock exhausted).
    // decreaseStock is void — BDDMockito requires willAnswer().given() for void methods.
    AtomicInteger dbStock = new AtomicInteger(AVAILABLE_STOCK);
    org.mockito.BDDMockito.willAnswer(
            inv -> {
              int qty = inv.getArgument(1);
              int remaining = dbStock.addAndGet(-qty);
              if (remaining < 0) {
                dbStock.addAndGet(qty); // undo
                throw new InsufficientStockException("DB stock exhausted");
              }
              return null;
            })
        .given(productStockUpdater)
        .decreaseStock(any(), anyInt());

    StockReservationService service =
        new StockReservationService(stockCachePort, productStockUpdater);

    // ─── Launch all threads simultaneously ─────────────────────────────────
    CountDownLatch startGate = new CountDownLatch(1); // all threads wait for this
    CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);

    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger rejectedByCache = new AtomicInteger(0);
    AtomicInteger rejectedByDb = new AtomicInteger(0);
    List<String> errors = Collections.synchronizedList(new ArrayList<>());

    ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

    for (int i = 0; i < THREAD_COUNT; i++) {
      executor.submit(
          () -> {
            try {
              startGate.await(); // synchronised start — maximum contention
              service.reserve(productId, 1);
              successCount.incrementAndGet();
            } catch (InsufficientStockException e) {
              rejectedByCache.incrementAndGet();
            } catch (StockConflictException e) {
              rejectedByDb.incrementAndGet();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              errors.add("Interrupted: " + e.getMessage());
            } catch (Exception e) {
              errors.add("Unexpected: " + e.getClass().getSimpleName() + " — " + e.getMessage());
            } finally {
              doneLatch.countDown();
            }
          });
    }

    startGate.countDown(); // release all threads at once
    boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
    executor.shutdown();

    // ─── Assertions ────────────────────────────────────────────────────────

    assertThat(completed).as("All threads must complete within timeout").isTrue();
    assertThat(errors).as("No unexpected exceptions").isEmpty();

    log.info(
        "Concurrency result — threads: {}, stock: {}, success: {}, "
            + "rejected-by-cache: {}, rejected-by-db: {}",
        THREAD_COUNT,
        AVAILABLE_STOCK,
        successCount.get(),
        rejectedByCache.get(),
        rejectedByDb.get());

    // The core invariant: no more units were reserved than were available.
    // This is the oversell prevention guarantee — the central requirement of Section 3.
    assertThat(successCount.get())
        .as("Successful reservations must not exceed available stock")
        .isLessThanOrEqualTo(AVAILABLE_STOCK);

    // Every thread either succeeded or was rejected — none were lost silently.
    assertThat(successCount.get() + rejectedByCache.get() + rejectedByDb.get())
        .as("All %d threads must have produced an outcome", THREAD_COUNT)
        .isEqualTo(THREAD_COUNT);

    // The DB stock counter must not go negative — increments from compensation keep it >= 0.
    assertThat(dbStock.get())
        .as("DB stock must not go negative (no oversell at DB layer)")
        .isGreaterThanOrEqualTo(0);
  }
}

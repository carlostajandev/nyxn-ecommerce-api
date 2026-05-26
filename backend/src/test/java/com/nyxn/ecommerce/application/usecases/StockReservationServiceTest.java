package com.nyxn.ecommerce.application.usecases;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.nyxn.ecommerce.domain.exceptions.InsufficientStockException;
import com.nyxn.ecommerce.domain.ports.out.StockCachePort;
import com.nyxn.ecommerce.domain.valueobject.ProductId;
import com.nyxn.ecommerce.infrastructure.stock.ProductStockUpdater;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link StockReservationService}.
 *
 * <p>These tests validate the orchestration logic between the Redis cache and the DB updater. Both
 * dependencies are mocked — no Redis, no PostgreSQL, no Docker needed. This validates:
 *
 * <ul>
 *   <li>Cache hit → DB update path
 *   <li>Cache miss → DB-only path (no compensation needed)
 *   <li>Cache insufficient stock → immediate rejection, DB never touched
 *   <li>DB failure after cache success → cache compensated (incremented back)
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class StockReservationServiceTest {

  @Mock private StockCachePort stockCachePort;
  @Mock private ProductStockUpdater productStockUpdater;

  private StockReservationService service;

  @BeforeEach
  void setUp() {
    service = new StockReservationService(stockCachePort, productStockUpdater);
  }

  @Test
  void reserve_whenCacheHitAndDbSucceeds_thenStockReserved() {
    ProductId productId = ProductId.generate();
    // Cache returns positive remaining stock — reservation allowed
    given(stockCachePort.tryDecrement(any(), anyInt())).willReturn(50L);

    service.reserve(productId, 10);

    then(stockCachePort).should().tryDecrement(productId, 10);
    then(productStockUpdater).should().decreaseStock(productId, 10);
    // No compensation — DB write succeeded
    then(stockCachePort).should(never()).increment(any(), anyInt());
  }

  @Test
  void reserve_whenCacheMiss_thenDbOnlyPathUsed() {
    ProductId productId = ProductId.generate();
    // -1 signals cache miss — key not present in Redis
    given(stockCachePort.tryDecrement(any(), anyInt())).willReturn(-1L);

    service.reserve(productId, 5);

    then(productStockUpdater).should().decreaseStock(productId, 5);
    // Nothing to compensate — cache was not decremented
    then(stockCachePort).should(never()).increment(any(), anyInt());
  }

  @Test
  void reserve_whenCacheReportsInsufficientStock_thenRejectWithoutTouchingDb() {
    ProductId productId = ProductId.generate();
    // -2 means Redis knows stock is insufficient
    given(stockCachePort.tryDecrement(any(), anyInt())).willReturn(-2L);

    assertThatThrownBy(() -> service.reserve(productId, 200))
        .isInstanceOf(InsufficientStockException.class)
        .hasMessageContaining("Insufficient stock");

    // Critical: DB must never be called when cache already rejected the request
    then(productStockUpdater).should(never()).decreaseStock(any(), anyInt());
    then(stockCachePort).should(never()).increment(any(), anyInt());
  }

  @Test
  void reserve_whenCacheSucceedsButDbFails_thenCacheCompensated() {
    ProductId productId = ProductId.generate();
    given(stockCachePort.tryDecrement(any(), anyInt())).willReturn(10L);
    // DB write fails for any reason (e.g. StockConflictException after retries).
    // decreaseStock is void so BDDMockito requires the willThrow-given form for void methods.
    org.mockito.BDDMockito.willThrow(new InsufficientStockException("DB stock exhausted"))
        .given(productStockUpdater)
        .decreaseStock(any(), anyInt());

    assertThatThrownBy(() -> service.reserve(productId, 5))
        .isInstanceOf(InsufficientStockException.class);

    // The Redis decrement must be undone — without this, the cache under-counts permanently.
    // This is the compensating transaction that maintains eventual consistency.
    then(stockCachePort).should().increment(productId, 5);
  }

  @Test
  void release_thenCacheAndDbBothIncremented() {
    ProductId productId = ProductId.generate();

    service.release(productId, 3);

    then(stockCachePort).should().increment(productId, 3);
    then(productStockUpdater).should().increaseStock(productId, 3);
  }
}

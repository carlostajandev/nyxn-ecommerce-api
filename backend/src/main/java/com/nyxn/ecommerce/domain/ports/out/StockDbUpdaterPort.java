package com.nyxn.ecommerce.domain.ports.out;

import com.nyxn.ecommerce.domain.valueobject.ProductId;

/**
 * Outbound port for persisting stock changes to the database.
 *
 * <p>Why a dedicated port instead of reusing {@link ProductRepository}?
 * {@code ProductRepository} is a general-purpose CRUD port. Stock mutation in a
 * flash-sale scenario has distinct non-functional requirements: optimistic-locking
 * retry, exponential backoff, and a recovery path after exhausting retries. Mixing
 * those concerns into the generic repository port would violate SRP and force every
 * caller to deal with retry semantics.
 *
 * <p>By isolating the contract here, the application layer ({@code StockReservationService})
 * depends on a minimal interface that expresses intent without knowing anything about
 * Spring Retry, {@code @Transactional}, or Hibernate — all of which are infrastructure
 * details belonging to the concrete adapter ({@code ProductStockUpdater}).
 */
public interface StockDbUpdaterPort {

  /**
   * Decreases the stock of the given product by {@code quantity} units.
   *
   * <p>Implementations are expected to enforce optimistic locking (version check)
   * and retry on transient conflicts.
   *
   * @throws com.nyxn.ecommerce.domain.exceptions.InsufficientStockException if the DB
   *     confirms the product has fewer units than requested.
   * @throws com.nyxn.ecommerce.domain.exceptions.StockConflictException if all retry
   *     attempts are exhausted due to concurrent modifications.
   */
  void decreaseStock(ProductId productId, int quantity);

  /**
   * Increases the stock of the given product by {@code quantity} units.
   *
   * <p>Used for stock releases (order cancellation, reservation rollback).
   */
  void increaseStock(ProductId productId, int quantity);
}

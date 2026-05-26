package com.nyxn.ecommerce.domain.ports.in;

import com.nyxn.ecommerce.domain.valueobject.ProductId;

/**
 * Inbound port: high-throughput stock reservation for flash-sale and peak-traffic scenarios.
 *
 * <p>This use case is separate from the general {@link UpdateProductUseCase} stock management
 * because it has different performance and correctness requirements:
 *
 * <ul>
 *   <li><b>Performance:</b> hundreds of concurrent callers; must resolve at the Redis layer before
 *       touching PostgreSQL.
 *   <li><b>Correctness:</b> must never allow negative stock; must compensate the cache if the DB
 *       write fails.
 *   <li><b>Resilience:</b> transient optimistic-lock conflicts must be retried transparently; only
 *       persistent conflicts are surfaced as HTTP 409.
 * </ul>
 *
 * <p>The separation also satisfies ISP: a controller that handles reservations depends only on this
 * interface, not on the broader product-management contract.
 */
public interface ReserveStockUseCase {

  /**
   * Reserves {@code quantity} units of the specified product.
   *
   * <p>The implementation tries the Redis cache first; on success it commits the deduction to
   * PostgreSQL with optimistic-lock retry. If stock is insufficient at either layer the operation
   * is rejected and no state is changed.
   *
   * @param productId the product to reserve stock for
   * @param quantity the number of units to reserve; must be positive
   * @throws com.nyxn.ecommerce.domain.exceptions.InsufficientStockException if stock is
   *     insufficient at either the cache or the database layer
   * @throws com.nyxn.ecommerce.domain.exceptions.StockConflictException if optimistic locking
   *     conflicts persist after all retry attempts
   */
  void reserve(ProductId productId, int quantity);

  /**
   * Releases {@code quantity} units back to the product's available stock.
   *
   * <p>Called when an order is cancelled or payment fails after a reservation.
   *
   * @param productId the product whose stock is released
   * @param quantity the number of units to return
   */
  void release(ProductId productId, int quantity);
}

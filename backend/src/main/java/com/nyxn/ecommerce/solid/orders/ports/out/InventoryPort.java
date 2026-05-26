package com.nyxn.ecommerce.solid.orders.ports.out;

import com.nyxn.ecommerce.domain.valueobject.ProductId;

/**
 * Outbound port: inventory operations.
 *
 * <p>DIP fix: the application service needs to know whether stock is available and needs to
 * decrement it — that's a business need, not a technical choice. This interface expresses the
 * business need. The concrete adapter (backed by JDBC, another microservice, or an in-memory stub
 * in tests) is injected at runtime.
 *
 * <p>SRP fix: inventory logic is now an isolated concern. A change to how stock is tracked (e.g.
 * migrating from a local database to a dedicated Inventory service) only touches the adapter, not
 * the core order service.
 */
public interface InventoryPort {

  /**
   * Checks whether the requested quantity is available for the given product.
   *
   * @param productId the product to check
   * @param quantity the quantity needed
   * @return {@code true} if stock is sufficient
   */
  boolean isAvailable(ProductId productId, int quantity);

  /**
   * Decrements stock by the requested quantity.
   *
   * <p>Callers must verify availability before calling this method. Decrementing without an
   * availability check is a logic error that the port cannot prevent — that guard belongs in the
   * application service.
   *
   * @param productId the product whose stock is reduced
   * @param quantity the number of units to deduct
   */
  void deduct(ProductId productId, int quantity);
}

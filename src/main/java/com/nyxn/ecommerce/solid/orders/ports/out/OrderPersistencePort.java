package com.nyxn.ecommerce.solid.orders.ports.out;

import com.nyxn.ecommerce.solid.orders.domain.Order;
import com.nyxn.ecommerce.solid.orders.domain.OrderId;
import java.util.Optional;

/**
 * Outbound port: order persistence.
 *
 * <p>DIP fix: the application service saves and retrieves orders through this abstraction. It has
 * no knowledge of JPA, SQL, or any specific database technology. The concrete JPA adapter in {@code
 * infrastructure/persistence} implements this interface and is wired in at startup.
 *
 * <p>This boundary is what makes the domain testable with a plain {@code HashMap} in unit tests —
 * no database container required.
 */
public interface OrderPersistencePort {

  /**
   * Persists a new order or updates an existing one.
   *
   * @param order the aggregate to persist
   * @return the persisted aggregate (may differ if the adapter assigns database-generated fields)
   */
  Order save(Order order);

  /**
   * Retrieves an order by its identifier.
   *
   * @param id the order identifier
   * @return the order if found, empty otherwise
   */
  Optional<Order> findById(OrderId id);
}

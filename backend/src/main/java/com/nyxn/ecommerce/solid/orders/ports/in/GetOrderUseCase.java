package com.nyxn.ecommerce.solid.orders.ports.in;

import com.nyxn.ecommerce.solid.orders.domain.Order;
import com.nyxn.ecommerce.solid.orders.domain.OrderId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Inbound port: order retrieval.
 *
 * <p>Separated from {@link PlaceOrderUseCase} — ISP says a REST endpoint that only reads orders
 * should not depend on the write-side contract. Controllers and tests for GET endpoints mock only
 * this interface.
 */
public interface GetOrderUseCase {

  /**
   * Returns the order with the given identifier.
   *
   * @param id the order to retrieve
   * @return the order aggregate
   * @throws com.nyxn.ecommerce.solid.orders.domain.OrderNotFoundException if no such order exists
   */
  Order findById(OrderId id);

  /**
   * Returns a paginated view of all orders.
   *
   * @param pageable pagination and sort parameters
   * @return page of order aggregates
   */
  Page<Order> findAll(Pageable pageable);
}

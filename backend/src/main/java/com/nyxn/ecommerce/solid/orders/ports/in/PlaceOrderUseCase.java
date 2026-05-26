package com.nyxn.ecommerce.solid.orders.ports.in;

import com.nyxn.ecommerce.solid.orders.domain.Order;

/**
 * Inbound port: the only contract the delivery layer (REST, messaging) uses to trigger order
 * placement.
 *
 * <p>ISP fix: this interface carries exactly one responsibility — placing an order. The legacy
 * {@link com.nyxn.ecommerce.solid.legacy.OrderOperations} mixed order processing and report
 * generation. Here those concerns are expressed as separate, narrow interfaces. A controller that
 * only places orders never sees the reporting API; a scheduler that only generates reports never
 * sees the order API. Neither has to stub the other in tests.
 *
 * <p>DIP fix: the controller depends on <em>this interface</em>, not on a concrete use-case class.
 * The concrete implementation ({@link
 * com.nyxn.ecommerce.solid.orders.application.PlaceOrderService}) lives in the application layer
 * and is injected at runtime by Spring. The controller is oblivious to the implementation and can
 * be tested against a simple in-memory stub.
 */
public interface PlaceOrderUseCase {

  /**
   * Places a new order for the given customer.
   *
   * @param command validated command carrying all placement data
   * @return the persisted {@link Order} aggregate in CONFIRMED status
   */
  Order execute(PlaceOrderCommand command);
}

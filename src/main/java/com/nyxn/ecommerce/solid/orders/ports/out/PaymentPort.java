package com.nyxn.ecommerce.solid.orders.ports.out;

import java.math.BigDecimal;

/**
 * Outbound port: payment capture.
 *
 * <p>DIP fix: the domain defines <em>what</em> it needs (charge a customer) without specifying
 * <em>how</em> (Stripe, PayPal, crypto). The concrete adapter that implements this interface lives
 * in {@code infrastructure/payment} and is injected by Spring at startup.
 *
 * <p>OCP fix: adding a new payment provider means writing a new adapter class that implements this
 * interface. The application service ({@link
 * com.nyxn.ecommerce.solid.orders.application.PlaceOrderService}) is untouched. The legacy approach
 * required a new {@code else if} in the core service for every provider.
 *
 * <p>Test impact: unit tests for {@link
 * com.nyxn.ecommerce.solid.orders.application.PlaceOrderService} mock this interface with two lines
 * of Mockito — no real HTTP calls, no credentials needed.
 */
public interface PaymentPort {

  /**
   * Captures payment from the given customer for the specified amount.
   *
   * @param customerId the customer being charged
   * @param amount the total amount to capture
   * @return a payment provider reference that can be stored on the order for reconciliation
   */
  String charge(String customerId, BigDecimal amount);
}

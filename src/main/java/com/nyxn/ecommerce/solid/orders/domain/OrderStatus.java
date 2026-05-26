package com.nyxn.ecommerce.solid.orders.domain;

/**
 * Life-cycle states of an Order.
 *
 * <p>Encoding valid transitions as an enum (rather than free-form strings) makes illegal state
 * transitions a compile-time error. An {@code Order} can only be moved to a state defined here, and
 * switch-expressions over this enum will produce a compile warning when a new state is added but
 * not handled.
 */
public enum OrderStatus {
  PENDING,
  PAYMENT_CONFIRMED,
  STOCK_RESERVED,
  CONFIRMED,
  FAILED
}

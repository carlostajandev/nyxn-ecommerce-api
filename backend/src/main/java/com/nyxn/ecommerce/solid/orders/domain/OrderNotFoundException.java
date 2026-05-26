package com.nyxn.ecommerce.solid.orders.domain;

import com.nyxn.ecommerce.domain.exceptions.DomainException;

/**
 * Thrown when a requested order does not exist in the system.
 *
 * <p>Extends {@link DomainException} so that it remains a {@code RuntimeException} — no checked
 * exception leaks through port signatures. The global exception handler maps this to HTTP 404.
 */
public class OrderNotFoundException extends DomainException {

  public OrderNotFoundException(OrderId id) {
    super("Order not found: " + id);
  }
}

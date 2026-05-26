package com.nyxn.ecommerce.solid.orders.application;

import com.nyxn.ecommerce.domain.exceptions.DomainException;

/**
 * Thrown when an order cannot be fulfilled because the requested quantity exceeds available stock.
 *
 * <p>Extends {@link DomainException} (which is a {@code RuntimeException}) to avoid polluting
 * method signatures with checked exceptions. The global exception handler maps this to HTTP 422
 * Unprocessable Entity.
 */
public class InsufficientOrderStockException extends DomainException {

  public InsufficientOrderStockException(String message) {
    super(message);
  }
}

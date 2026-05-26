package com.nyxn.ecommerce.domain.exceptions;

/**
 * Thrown when two concurrent transactions attempt to modify the stock of the same product.
 *
 * <p>Mapped to {@code 409 CONFLICT} by the global exception handler so the client can retry with
 * exponential backoff instead of receiving a generic {@code 500}.
 */
public class StockConflictException extends DomainException {

  public StockConflictException(String message) {
    super(message);
  }
}

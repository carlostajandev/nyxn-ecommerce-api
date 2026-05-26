package com.nyxn.ecommerce.domain.exceptions;

public class InsufficientStockException extends DomainException {

  public InsufficientStockException(String message) {
    super(message);
  }
}

package com.nyxn.ecommerce.domain.exceptions;

/** Lanzada cuando hay conflicto de concurrencia optimista sobre el stock. Mapea a 409 CONFLICT. */
public class StockConflictException extends DomainException {

  public StockConflictException(String message) {
    super(message);
  }
}

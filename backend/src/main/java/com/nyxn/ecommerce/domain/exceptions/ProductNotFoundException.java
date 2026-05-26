package com.nyxn.ecommerce.domain.exceptions;

import com.nyxn.ecommerce.domain.valueobject.ProductId;

public class ProductNotFoundException extends DomainException {

  public ProductNotFoundException(ProductId id) {
    super("Product not found with id: " + id);
  }
}

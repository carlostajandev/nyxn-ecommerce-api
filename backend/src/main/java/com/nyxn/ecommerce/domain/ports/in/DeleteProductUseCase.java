package com.nyxn.ecommerce.domain.ports.in;

import com.nyxn.ecommerce.domain.valueobject.ProductId;

public interface DeleteProductUseCase {

  void execute(ProductId id);
}

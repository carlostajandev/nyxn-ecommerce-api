package com.nyxn.ecommerce.domain.ports.in;

import com.nyxn.ecommerce.application.dto.UpdateProductCommand;
import com.nyxn.ecommerce.domain.model.Product;
import com.nyxn.ecommerce.domain.valueobject.ProductId;

public interface UpdateProductUseCase {

  Product execute(ProductId id, UpdateProductCommand command);
}

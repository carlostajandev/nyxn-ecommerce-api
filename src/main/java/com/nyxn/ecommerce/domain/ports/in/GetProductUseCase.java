package com.nyxn.ecommerce.domain.ports.in;

import com.nyxn.ecommerce.domain.model.Product;
import com.nyxn.ecommerce.domain.valueobject.ProductId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface GetProductUseCase {

  Product findById(ProductId id);

  Page<Product> findAll(Pageable pageable);
}

package com.nyxn.ecommerce.application.usecases;

import com.nyxn.ecommerce.domain.exceptions.ProductNotFoundException;
import com.nyxn.ecommerce.domain.model.Product;
import com.nyxn.ecommerce.domain.ports.in.GetProductUseCase;
import com.nyxn.ecommerce.domain.ports.out.ProductRepository;
import com.nyxn.ecommerce.domain.valueobject.ProductId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetProductUseCaseImpl implements GetProductUseCase {

  private final ProductRepository productRepository;

  public GetProductUseCaseImpl(ProductRepository productRepository) {
    this.productRepository = productRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public Product findById(ProductId id) {
    return productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
  }

  @Override
  @Transactional(readOnly = true)
  public Page<Product> findAll(Pageable pageable) {
    return productRepository.findAll(pageable);
  }
}

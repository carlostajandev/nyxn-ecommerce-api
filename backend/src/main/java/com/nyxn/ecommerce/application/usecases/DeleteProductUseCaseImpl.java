package com.nyxn.ecommerce.application.usecases;

import com.nyxn.ecommerce.domain.exceptions.ProductNotFoundException;
import com.nyxn.ecommerce.domain.ports.in.DeleteProductUseCase;
import com.nyxn.ecommerce.domain.ports.out.ProductEventPublisher;
import com.nyxn.ecommerce.domain.ports.out.ProductRepository;
import com.nyxn.ecommerce.domain.valueobject.ProductId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeleteProductUseCaseImpl implements DeleteProductUseCase {

  private final ProductRepository productRepository;
  private final ProductEventPublisher eventPublisher;

  public DeleteProductUseCaseImpl(
      ProductRepository productRepository, ProductEventPublisher eventPublisher) {
    this.productRepository = productRepository;
    this.eventPublisher = eventPublisher;
  }

  @Override
  @Transactional
  public void execute(ProductId id) {
    if (!productRepository.existsById(id)) {
      throw new ProductNotFoundException(id);
    }
    productRepository.deleteById(id);
    eventPublisher.publishProductDeleted(id.getValue().toString());
  }
}

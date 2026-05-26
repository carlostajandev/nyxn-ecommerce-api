package com.nyxn.ecommerce.application.usecases;

import com.nyxn.ecommerce.application.dto.CreateProductCommand;
import com.nyxn.ecommerce.domain.model.Product;
import com.nyxn.ecommerce.domain.ports.in.CreateProductUseCase;
import com.nyxn.ecommerce.domain.ports.out.ProductEventPublisher;
import com.nyxn.ecommerce.domain.ports.out.ProductRepository;
import com.nyxn.ecommerce.domain.valueobject.Money;
import com.nyxn.ecommerce.domain.valueobject.ProductId;
import com.nyxn.ecommerce.domain.valueobject.Stock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateProductUseCaseImpl implements CreateProductUseCase {

  private final ProductRepository productRepository;
  private final ProductEventPublisher eventPublisher;

  public CreateProductUseCaseImpl(
      ProductRepository productRepository, ProductEventPublisher eventPublisher) {
    this.productRepository = productRepository;
    this.eventPublisher = eventPublisher;
  }

  @Override
  @Transactional
  public Product execute(CreateProductCommand command) {
    Product product =
        Product.builder()
            .id(ProductId.generate())
            .name(command.name())
            .description(command.description())
            .price(Money.ofUSD(command.price()))
            .stock(Stock.of(command.stock()))
            .category(command.category())
            .build();

    Product saved = productRepository.save(product);

    // Publishing the event outside the transaction introduces at-most-once delivery risk.
    // The correct production pattern is Outbox: persist the event in the same transaction and
    // publish it asynchronously from a worker. This trade-off is acceptable for this scope.
    eventPublisher.publishProductCreated(saved);

    return saved;
  }
}

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

  // Inyección por constructor — obligatorio. Facilita test sin contexto Spring.
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

    // Publicación de evento fuera de transacción no es correcto aquí para garantía exactamente-una-
    // vez. En producción usaría Outbox Pattern: el evento se persiste en la misma tx y un worker lo
    // publica. Este trade-off se acepta para el scope de la prueba.
    eventPublisher.publishProductCreated(saved);

    return saved;
  }
}

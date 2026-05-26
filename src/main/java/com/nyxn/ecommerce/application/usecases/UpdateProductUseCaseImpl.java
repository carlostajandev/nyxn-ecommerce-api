package com.nyxn.ecommerce.application.usecases;

import com.nyxn.ecommerce.application.dto.UpdateProductCommand;
import com.nyxn.ecommerce.domain.exceptions.ProductNotFoundException;
import com.nyxn.ecommerce.domain.model.Product;
import com.nyxn.ecommerce.domain.ports.in.UpdateProductUseCase;
import com.nyxn.ecommerce.domain.ports.out.ProductEventPublisher;
import com.nyxn.ecommerce.domain.ports.out.ProductRepository;
import com.nyxn.ecommerce.domain.valueobject.Money;
import com.nyxn.ecommerce.domain.valueobject.ProductId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateProductUseCaseImpl implements UpdateProductUseCase {

  private final ProductRepository productRepository;
  private final ProductEventPublisher eventPublisher;

  public UpdateProductUseCaseImpl(
      ProductRepository productRepository, ProductEventPublisher eventPublisher) {
    this.productRepository = productRepository;
    this.eventPublisher = eventPublisher;
  }

  @Override
  @Transactional
  public Product execute(ProductId id, UpdateProductCommand command) {
    Product product =
        productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));

    product.updateDetails(command.name(), command.description(), command.category());
    product.reprice(Money.ofUSD(command.price()));

    // Route stock changes through domain methods so invariants (no negative stock) are enforced.
    // A direct setter would bypass that protection.
    int currentQuantity = product.getStock().getQuantity();
    int delta = command.stock() - currentQuantity;
    if (delta > 0) {
      product.increaseStock(delta);
    } else if (delta < 0) {
      product.decreaseStock(Math.abs(delta));
    }

    Product updated = productRepository.save(product);
    eventPublisher.publishProductUpdated(updated);
    return updated;
  }
}

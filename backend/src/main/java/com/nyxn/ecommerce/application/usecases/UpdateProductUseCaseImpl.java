package com.nyxn.ecommerce.application.usecases;

import com.nyxn.ecommerce.application.dto.UpdateProductCommand;
import com.nyxn.ecommerce.domain.exceptions.ProductNotFoundException;
import com.nyxn.ecommerce.domain.model.Product;
import com.nyxn.ecommerce.domain.ports.in.UpdateProductUseCase;
import com.nyxn.ecommerce.domain.ports.out.ProductEventPublisher;
import com.nyxn.ecommerce.domain.ports.out.ProductRepository;
import com.nyxn.ecommerce.domain.valueobject.Money;
import com.nyxn.ecommerce.domain.valueobject.ProductId;
import com.nyxn.ecommerce.infrastructure.config.RedisConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
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
  @Caching(
      evict = {
        // Price or category changes shift the top-products ranking and revenue aggregates.
        // Evict both analytics caches so the next dashboard read reflects the updated state.
        @CacheEvict(value = RedisConfig.CACHE_ANALYTICS_TOP, allEntries = true),
        @CacheEvict(value = RedisConfig.CACHE_ANALYTICS_REVENUE, allEntries = true),
        // Stock changes affect the low-stock alert view; evict to avoid stale urgency tiers.
        @CacheEvict(value = RedisConfig.CACHE_ANALYTICS_LOW_STOCK, allEntries = true),
        // Evict the product list cache so callers see updated price/description immediately.
        @CacheEvict(value = RedisConfig.CACHE_PRODUCTS, allEntries = true)
      })
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

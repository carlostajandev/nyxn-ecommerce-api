package com.nyxn.ecommerce.solid.orders.infrastructure.inventory;

import com.nyxn.ecommerce.domain.ports.out.ProductRepository;
import com.nyxn.ecommerce.domain.valueobject.ProductId;
import com.nyxn.ecommerce.solid.orders.ports.out.InventoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Implements {@link InventoryPort} by delegating to the existing {@link ProductRepository}.
 *
 * <p>This adapter bridges two hexagons: the Orders domain needs to check and deduct stock, but
 * stock data lives in the Products domain. Rather than giving the Orders domain direct access to
 * the Products JPA repository (which would create a layering violation), this adapter sits in the
 * infrastructure layer and orchestrates the call through the Products outbound port.
 *
 * <p>SRP: a change to how stock is stored (e.g. moving to a dedicated Inventory microservice) only
 * touches this class. The {@link com.nyxn.ecommerce.solid.orders.application.PlaceOrderService}
 * calls {@code inventory.isAvailable()} — it does not care whether that check hits a local database
 * or an HTTP endpoint.
 */
@Component
public class ProductRepositoryInventoryAdapter implements InventoryPort {

  private static final Logger log =
      LoggerFactory.getLogger(ProductRepositoryInventoryAdapter.class);

  private final ProductRepository productRepository;

  public ProductRepositoryInventoryAdapter(ProductRepository productRepository) {
    this.productRepository = productRepository;
  }

  @Override
  public boolean isAvailable(ProductId productId, int quantity) {
    return productRepository
        .findById(productId)
        .map(product -> product.getStock().getQuantity() >= quantity)
        .orElse(false);
  }

  @Override
  public void deduct(ProductId productId, int quantity) {
    productRepository
        .findById(productId)
        .ifPresent(
            product -> {
              product.decreaseStock(quantity);
              productRepository.save(product);
              log.debug(
                  "Deducted {} units from product {}; remaining stock: {}",
                  quantity,
                  productId,
                  product.getStock().getQuantity());
            });
  }
}

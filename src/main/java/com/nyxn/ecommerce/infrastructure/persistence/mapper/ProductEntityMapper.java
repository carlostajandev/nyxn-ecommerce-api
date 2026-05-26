package com.nyxn.ecommerce.infrastructure.persistence.mapper;

import com.nyxn.ecommerce.domain.model.Product;
import com.nyxn.ecommerce.domain.valueobject.Money;
import com.nyxn.ecommerce.domain.valueobject.ProductId;
import com.nyxn.ecommerce.domain.valueobject.Stock;
import com.nyxn.ecommerce.infrastructure.persistence.entity.ProductEntity;
import org.springframework.stereotype.Component;

/**
 * Translates between the JPA entity and the domain aggregate.
 *
 * <p>This is the only class where {@code ProductEntity} and {@code Product} know about each other.
 * Isolating that translation here ensures a schema change (renaming a column, adding a technical
 * field) never pollutes the business model.
 */
@Component
public class ProductEntityMapper {

  public Product toDomain(ProductEntity entity) {
    return Product.builder()
        .id(ProductId.of(entity.getId()))
        .name(entity.getName())
        .description(entity.getDescription())
        .price(Money.of(entity.getPrice(), entity.getCurrency()))
        .stock(Stock.of(entity.getStock()))
        .category(entity.getCategory())
        .createdAt(entity.getCreatedAt())
        .build();
  }

  public ProductEntity toEntity(Product product) {
    ProductEntity entity = new ProductEntity();
    entity.setId(product.getId().getValue());
    entity.setName(product.getName());
    entity.setDescription(product.getDescription());
    entity.setPrice(product.getPrice().getAmount());
    entity.setCurrency(product.getPrice().getCurrency());
    entity.setStock(product.getStock().getQuantity());
    entity.setCategory(product.getCategory());
    entity.setCreatedAt(product.getCreatedAt());
    return entity;
  }
}

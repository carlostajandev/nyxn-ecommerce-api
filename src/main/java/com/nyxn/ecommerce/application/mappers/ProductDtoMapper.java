package com.nyxn.ecommerce.application.mappers;

import com.nyxn.ecommerce.application.dto.ProductResponse;
import com.nyxn.ecommerce.domain.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * Mapper manual: domain → DTO de respuesta. Elegí mapper manual sobre MapStruct aquí porque la
 * lógica de extracción de value objects (Money, Stock, ProductId) no es trivial para el generador.
 * MapStruct se usa en el adaptador de persistencia (entity ↔ domain) donde el mapeo es campo a
 * campo.
 */
@Component
public class ProductDtoMapper {

  public ProductResponse toResponse(Product product) {
    return new ProductResponse(
        product.getId().getValue(),
        product.getName(),
        product.getDescription(),
        product.getPrice().getAmount(),
        product.getPrice().getCurrency(),
        product.getStock().getQuantity(),
        product.getCategory(),
        product.getCreatedAt());
  }

  public Page<ProductResponse> toResponsePage(Page<Product> products) {
    return products.map(this::toResponse);
  }
}

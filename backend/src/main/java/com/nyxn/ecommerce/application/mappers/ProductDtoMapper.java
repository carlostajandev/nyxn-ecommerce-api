package com.nyxn.ecommerce.application.mappers;

import com.nyxn.ecommerce.application.dto.ProductResponse;
import com.nyxn.ecommerce.domain.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * Maps the {@code Product} aggregate to the HTTP response DTO.
 *
 * <p>Manual mapping chosen over MapStruct because unwrapping value objects ({@code Money}, {@code
 * Stock}, {@code ProductId}) requires custom logic that MapStruct cannot infer without verbose
 * configuration. MapStruct is used in the persistence layer where mapping is field-to-field with no
 * extra logic.
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

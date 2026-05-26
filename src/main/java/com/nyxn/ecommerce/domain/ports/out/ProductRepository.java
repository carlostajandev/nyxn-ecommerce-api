package com.nyxn.ecommerce.domain.ports.out;

import com.nyxn.ecommerce.domain.model.Product;
import com.nyxn.ecommerce.domain.valueobject.ProductId;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Puerto de salida: el dominio declara qué necesita de persistencia. No sabe si es PostgreSQL,
 * MongoDB o un Map en memoria. La implementación concreta (JPA) vive en infrastructure/persistence.
 */
public interface ProductRepository {

  Product save(Product product);

  Optional<Product> findById(ProductId id);

  Page<Product> findAll(Pageable pageable);

  void deleteById(ProductId id);

  boolean existsById(ProductId id);
}

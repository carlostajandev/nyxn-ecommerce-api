package com.nyxn.ecommerce.domain.ports.out;

import com.nyxn.ecommerce.domain.model.Product;
import com.nyxn.ecommerce.domain.valueobject.ProductId;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Outbound port: persistence contract imposed by the domain.
 *
 * <p>The domain declares what it needs; the infrastructure decides how to provide it. This
 * interface has no mention of JPA, SQL, or any storage technology. Swapping PostgreSQL for MongoDB
 * only requires a new adapter — no domain class changes.
 */
public interface ProductRepository {

  Product save(Product product);

  Optional<Product> findById(ProductId id);

  Page<Product> findAll(Pageable pageable);

  void deleteById(ProductId id);

  boolean existsById(ProductId id);
}

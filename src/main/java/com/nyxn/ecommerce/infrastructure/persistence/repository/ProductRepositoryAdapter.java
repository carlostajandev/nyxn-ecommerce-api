package com.nyxn.ecommerce.infrastructure.persistence.repository;

import com.nyxn.ecommerce.domain.model.Product;
import com.nyxn.ecommerce.domain.ports.out.ProductRepository;
import com.nyxn.ecommerce.domain.valueobject.ProductId;
import com.nyxn.ecommerce.infrastructure.persistence.entity.ProductEntity;
import com.nyxn.ecommerce.infrastructure.persistence.mapper.ProductEntityMapper;
import java.util.Optional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * Adaptador secundario (driven): implementa el puerto ProductRepository del dominio usando JPA.
 * Elegido @Component sobre @Repository porque @Repository agrega traduccion de excepciones JPA que
 * ya Spring Data maneja. El dominio no sabe que esto existe.
 */
@Component
public class ProductRepositoryAdapter implements ProductRepository {

  private final JpaProductRepository jpa;
  private final ProductEntityMapper mapper;

  public ProductRepositoryAdapter(JpaProductRepository jpa, ProductEntityMapper mapper) {
    this.jpa = jpa;
    this.mapper = mapper;
  }

  @Override
  @CacheEvict(value = "products", key = "#product.id.value")
  public Product save(Product product) {
    ProductEntity entity = mapper.toEntity(product);
    ProductEntity saved = jpa.save(entity);
    return mapper.toDomain(saved);
  }

  @Override
  @Cacheable(value = "products", key = "#id.value")
  public Optional<Product> findById(ProductId id) {
    return jpa.findById(id.getValue()).map(mapper::toDomain);
  }

  @Override
  public Page<Product> findAll(Pageable pageable) {
    return jpa.findAll(pageable).map(mapper::toDomain);
  }

  @Override
  @CacheEvict(value = "products", key = "#id.value")
  public void deleteById(ProductId id) {
    jpa.deleteById(id.getValue());
  }

  @Override
  public boolean existsById(ProductId id) {
    return jpa.existsById(id.getValue());
  }
}

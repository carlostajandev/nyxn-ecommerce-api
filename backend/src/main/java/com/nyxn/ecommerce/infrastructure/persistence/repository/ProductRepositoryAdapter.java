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
 * Secondary adapter: implements the {@code ProductRepository} domain port using JPA.
 *
 * <p>{@code @Component} used instead of {@code @Repository} because Spring Data already handles JPA
 * exception translation automatically. Adding {@code @Repository} would apply it twice with no
 * benefit.
 *
 * <p>{@code @CacheEvict} on {@code save} invalidates only the affected product entry. If a listing
 * cache is added later, it must also be evicted here.
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

package com.nyxn.ecommerce.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code products} table. Stays inside the infrastructure layer.
 *
 * <p>Separating the JPA entity from the {@code Product} aggregate lets the database schema and the
 * domain model evolve independently. A schema migration never forces a domain class change.
 *
 * <p>{@code @Version} enables optimistic locking at the JPA level. On a concurrent write conflict,
 * Hibernate throws {@code OptimisticLockException} before issuing the {@code UPDATE}, preventing
 * silent data overwrites. See Section 3 for the full concurrency handling strategy.
 */
@Entity
@Table(name = "products")
public class ProductEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "description", nullable = false, length = 500)
  private String description;

  @Column(name = "price", nullable = false, precision = 19, scale = 2)
  private BigDecimal price;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  @Column(name = "stock", nullable = false)
  private int stock;

  @Column(name = "category", nullable = false, length = 50)
  private String category;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  // Incremented by Hibernate on every UPDATE — drives optimistic locking conflict detection.
  @Version
  @Column(name = "version")
  private Long version;

  // JPA requires a no-arg constructor. Public so mappers in other packages can instantiate
  // this class directly without resorting to reflection.
  public ProductEntity() {}

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public void setPrice(BigDecimal price) {
    this.price = price;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public int getStock() {
    return stock;
  }

  public void setStock(int stock) {
    this.stock = stock;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Long getVersion() {
    return version;
  }
}

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
 * Entidad JPA: adaptador de persistencia. Nunca sale de este paquete hacia el dominio ni las
 * interfaces. La separación entidad/dominio permite evolucionar el schema sin afectar el modelo de
 * negocio y viceversa.
 *
 * <p>@Version habilita optimistic locking para Seccion 3 — control de concurrencia en stock.
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

  // Optimistic locking — incrementado por JPA en cada UPDATE
  @Version
  @Column(name = "version")
  private Long version;

  protected ProductEntity() {}

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

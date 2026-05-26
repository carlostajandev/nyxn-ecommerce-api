package com.nyxn.ecommerce.domain.model;

import com.nyxn.ecommerce.domain.valueobject.Money;
import com.nyxn.ecommerce.domain.valueobject.ProductId;
import com.nyxn.ecommerce.domain.valueobject.Stock;
import java.time.Instant;
import java.util.Objects;

/**
 * Agregado raíz del dominio de producto. Sin anotaciones de framework — este objeto es 100% POJO.
 * Toda regla de negocio relacionada con el producto vive aquí o en sus value objects. Los setters
 * están ausentes por diseño: la mutación se hace a través de métodos de dominio con nombres
 * explícitos que expresan intención.
 */
public class Product {

  private final ProductId id;
  private String name;
  private String description;
  private Money price;
  private Stock stock;
  private String category;
  private final Instant createdAt;

  private Product(Builder builder) {
    this.id = Objects.requireNonNull(builder.id, "id is required");
    this.name = Objects.requireNonNull(builder.name, "name is required");
    this.description = Objects.requireNonNull(builder.description, "description is required");
    this.price = Objects.requireNonNull(builder.price, "price is required");
    this.stock = Objects.requireNonNull(builder.stock, "stock is required");
    this.category = Objects.requireNonNull(builder.category, "category is required");
    this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
  }

  // ─── domain behavior ───────────────────────────────────────────────────────

  public void updateDetails(String name, String description, String category) {
    this.name = Objects.requireNonNull(name, "name is required");
    this.description = Objects.requireNonNull(description, "description is required");
    this.category = Objects.requireNonNull(category, "category is required");
  }

  public void reprice(Money newPrice) {
    this.price = Objects.requireNonNull(newPrice, "price is required");
  }

  public void decreaseStock(int quantity) {
    this.stock = this.stock.decrease(quantity);
  }

  public void increaseStock(int quantity) {
    this.stock = this.stock.increase(quantity);
  }

  public boolean isLowStock() {
    return stock.isLowerThan(10);
  }

  // ─── accessors ─────────────────────────────────────────────────────────────

  public ProductId getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public Money getPrice() {
    return price;
  }

  public Stock getStock() {
    return stock;
  }

  public String getCategory() {
    return category;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Product other)) return false;
    return id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  // ─── builder ───────────────────────────────────────────────────────────────

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private ProductId id;
    private String name;
    private String description;
    private Money price;
    private Stock stock;
    private String category;
    private Instant createdAt;

    public Builder id(ProductId id) {
      this.id = id;
      return this;
    }

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder description(String description) {
      this.description = description;
      return this;
    }

    public Builder price(Money price) {
      this.price = price;
      return this;
    }

    public Builder stock(Stock stock) {
      this.stock = stock;
      return this;
    }

    public Builder category(String category) {
      this.category = category;
      return this;
    }

    public Builder createdAt(Instant createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public Product build() {
      return new Product(this);
    }
  }
}

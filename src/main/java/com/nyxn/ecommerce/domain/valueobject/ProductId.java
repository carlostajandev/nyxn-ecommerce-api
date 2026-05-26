package com.nyxn.ecommerce.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

/**
 * Value object: identidad de un producto. Inmutable por diseño — dos ProductId con el mismo UUID
 * son iguales. No expone el UUID raw fuera del dominio; el adaptador de persistencia lo mapea.
 */
public final class ProductId {

  private final UUID value;

  private ProductId(UUID value) {
    this.value = Objects.requireNonNull(value, "ProductId cannot be null");
  }

  public static ProductId of(UUID value) {
    return new ProductId(value);
  }

  public static ProductId generate() {
    return new ProductId(UUID.randomUUID());
  }

  public UUID getValue() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ProductId other)) return false;
    return value.equals(other.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }

  @Override
  public String toString() {
    return value.toString();
  }
}

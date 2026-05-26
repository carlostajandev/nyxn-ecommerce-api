package com.nyxn.ecommerce.domain.valueobject;

import com.nyxn.ecommerce.domain.exceptions.InsufficientStockException;
import java.util.Objects;

/**
 * Value object para cantidad de stock. Encapsula la regla de negocio: el stock nunca puede ser
 * negativo. La validación vive aquí — no en un servicio de aplicación ni en un validator de Spring.
 */
public final class Stock {

  private final int quantity;

  private Stock(int quantity) {
    if (quantity < 0) {
      throw new InsufficientStockException("Stock cannot be negative: " + quantity);
    }
    this.quantity = quantity;
  }

  public static Stock of(int quantity) {
    return new Stock(quantity);
  }

  public static Stock zero() {
    return new Stock(0);
  }

  public Stock decrease(int amount) {
    if (amount > this.quantity) {
      throw new InsufficientStockException(
          "Cannot decrease stock by " + amount + ", available: " + this.quantity);
    }
    return new Stock(this.quantity - amount);
  }

  public Stock increase(int amount) {
    return new Stock(this.quantity + amount);
  }

  public boolean isLowerThan(int threshold) {
    return this.quantity < threshold;
  }

  public boolean isEmpty() {
    return this.quantity == 0;
  }

  public int getQuantity() {
    return quantity;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Stock other)) return false;
    return quantity == other.quantity;
  }

  @Override
  public int hashCode() {
    return Objects.hash(quantity);
  }

  @Override
  public String toString() {
    return String.valueOf(quantity);
  }
}

package com.nyxn.ecommerce.domain.valueobject;

import com.nyxn.ecommerce.domain.exceptions.InsufficientStockException;
import java.util.Objects;

/**
 * Quantity available in inventory for a given product.
 *
 * <p>The invariant "stock >= 0" is enforced here, not in an application service. Keeping it in the
 * value object ensures no entry point (event consumer, batch job, REST endpoint) can bypass the
 * rule by constructing a negative Stock directly.
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

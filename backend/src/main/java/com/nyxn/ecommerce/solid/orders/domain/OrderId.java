package com.nyxn.ecommerce.solid.orders.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Typed identifier for an Order aggregate.
 *
 * <p>A bare {@code UUID} or {@code String} passed across method boundaries gives the compiler no
 * way to detect a transposition (e.g. passing a {@code CustomerId} where an {@code OrderId} is
 * required). Wrapping the primitive in a named type catches that class of bug at compile time
 * instead of at runtime in production.
 */
public final class OrderId {

  private final UUID value;

  private OrderId(UUID value) {
    this.value = Objects.requireNonNull(value, "OrderId value must not be null");
  }

  public static OrderId generate() {
    return new OrderId(UUID.randomUUID());
  }

  public static OrderId of(UUID value) {
    return new OrderId(value);
  }

  public UUID value() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof OrderId other)) return false;
    return value.equals(other.value);
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }

  @Override
  public String toString() {
    return value.toString();
  }
}

package com.nyxn.ecommerce.solid.orders.domain;

import com.nyxn.ecommerce.domain.valueobject.Money;
import com.nyxn.ecommerce.domain.valueobject.ProductId;
import java.time.Instant;
import java.util.Objects;

/**
 * Order aggregate root.
 *
 * <p>Pure domain object: no Spring annotations, no JPA, no framework dependencies. The domain model
 * must be testable with plain {@code new} — if it requires a DI container to instantiate, the
 * architecture has leaked infrastructure concerns into the core.
 *
 * <p>State transitions are expressed as intention-revealing methods ({@link #confirmPayment},
 * {@link #reserveStock}, {@link #confirm}) rather than a generic {@code setStatus()} setter. This
 * encodes the valid life-cycle inside the aggregate and prevents callers from jumping to arbitrary
 * states.
 */
public class Order {

  private final OrderId id;
  private final String customerId;
  private final ProductId productId;
  private final int quantity;
  private final Money total;
  private final Instant createdAt;
  private OrderStatus status;
  private String paymentReference;

  private Order(Builder builder) {
    this.id = Objects.requireNonNull(builder.id, "id is required");
    this.customerId = Objects.requireNonNull(builder.customerId, "customerId is required");
    this.productId = Objects.requireNonNull(builder.productId, "productId is required");
    this.total = Objects.requireNonNull(builder.total, "total is required");
    if (builder.quantity < 1) {
      throw new IllegalArgumentException("quantity must be at least 1");
    }
    this.quantity = builder.quantity;
    // Allow the persistence mapper to restore a historical timestamp; default to now on creation.
    this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
    this.status = OrderStatus.PENDING;
  }

  // ─── State transition methods ───────────────────────────────────────────────

  /**
   * Records a successful payment capture.
   *
   * <p>Only valid from PENDING. Storing the reference on the aggregate keeps it queryable without
   * joining a payments table and makes the aggregate self-describing for audits.
   */
  public void confirmPayment(String paymentReference) {
    if (this.status != OrderStatus.PENDING) {
      throw new IllegalStateException("Cannot confirm payment on order in status: " + this.status);
    }
    this.paymentReference = Objects.requireNonNull(paymentReference, "paymentReference required");
    this.status = OrderStatus.PAYMENT_CONFIRMED;
  }

  /**
   * Records that inventory has been reserved.
   *
   * <p>Only valid after payment is confirmed — stock must never be held without a payment
   * commitment, to avoid blocking inventory for orders that will never settle.
   */
  public void reserveStock() {
    if (this.status != OrderStatus.PAYMENT_CONFIRMED) {
      throw new IllegalStateException(
          "Cannot reserve stock before payment is confirmed. Current status: " + this.status);
    }
    this.status = OrderStatus.STOCK_RESERVED;
  }

  /** Marks the order as fully confirmed — all placement steps succeeded. */
  public void confirm() {
    if (this.status != OrderStatus.STOCK_RESERVED) {
      throw new IllegalStateException(
          "Cannot confirm order before stock is reserved. Current status: " + this.status);
    }
    this.status = OrderStatus.CONFIRMED;
  }

  /**
   * Marks the order as failed. The reason is for observability; domain state tracks the outcome.
   */
  public void fail(String reason) {
    this.status = OrderStatus.FAILED;
  }

  // ─── Accessors ─────────────────────────────────────────────────────────────

  public OrderId getId() {
    return id;
  }

  public String getCustomerId() {
    return customerId;
  }

  public ProductId getProductId() {
    return productId;
  }

  public int getQuantity() {
    return quantity;
  }

  public Money getTotal() {
    return total;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public OrderStatus getStatus() {
    return status;
  }

  public String getPaymentReference() {
    return paymentReference;
  }

  // ─── Builder ───────────────────────────────────────────────────────────────

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private OrderId id;
    private String customerId;
    private ProductId productId;
    private int quantity;
    private Money total;
    private Instant createdAt;

    public Builder id(OrderId id) {
      this.id = id;
      return this;
    }

    public Builder customerId(String customerId) {
      this.customerId = customerId;
      return this;
    }

    public Builder productId(ProductId productId) {
      this.productId = productId;
      return this;
    }

    public Builder quantity(int quantity) {
      this.quantity = quantity;
      return this;
    }

    public Builder total(Money total) {
      this.total = total;
      return this;
    }

    /** Used by the persistence mapper to restore the original creation timestamp from the DB. */
    public Builder createdAt(Instant createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public Order build() {
      return new Order(this);
    }
  }

  // ─── Aggregate identity ────────────────────────────────────────────────────

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Order other)) return false;
    return id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }
}

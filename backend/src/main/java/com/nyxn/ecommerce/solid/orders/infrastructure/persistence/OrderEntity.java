package com.nyxn.ecommerce.solid.orders.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA representation of a persisted order.
 *
 * <p>This class is deliberately kept in the infrastructure layer — the domain aggregate {@link
 * com.nyxn.ecommerce.solid.orders.domain.Order} knows nothing about JPA. The entity is a flat,
 * annotated data holder; all business behaviour lives in the aggregate.
 *
 * <p>The {@code @Version} field drives optimistic locking: Hibernate appends {@code WHERE version =
 * ?} to every UPDATE, and increments the column if the row is found. A concurrent update that finds
 * a stale version raises {@code OptimisticLockException}, which the global exception handler maps
 * to HTTP 409 Conflict — the caller retries.
 *
 * <p>The no-arg constructor is {@code public} so the entity mapper in the same package can
 * instantiate it directly without resorting to reflection. JPA only requires the constructor to
 * exist, not that it be protected.
 */
@Entity
@Table(name = "orders")
public class OrderEntity {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "customer_id", nullable = false, length = 100)
  private String customerId;

  @Column(name = "product_id", nullable = false, updatable = false)
  private UUID productId;

  @Column(nullable = false)
  private int quantity;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(name = "payment_ref")
  private String paymentRef;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  // Incremented by Hibernate on every UPDATE — drives optimistic locking conflict detection.
  @Version
  @Column(nullable = false)
  private long version;

  public OrderEntity() {}

  // ─── Accessors ─────────────────────────────────────────────────────────────

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getCustomerId() {
    return customerId;
  }

  public void setCustomerId(String customerId) {
    this.customerId = customerId;
  }

  public UUID getProductId() {
    return productId;
  }

  public void setProductId(UUID productId) {
    this.productId = productId;
  }

  public int getQuantity() {
    return quantity;
  }

  public void setQuantity(int quantity) {
    this.quantity = quantity;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getPaymentRef() {
    return paymentRef;
  }

  public void setPaymentRef(String paymentRef) {
    this.paymentRef = paymentRef;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public long getVersion() {
    return version;
  }
}

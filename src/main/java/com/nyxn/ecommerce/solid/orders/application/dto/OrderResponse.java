package com.nyxn.ecommerce.solid.orders.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Outbound DTO for order data returned to callers.
 *
 * <p>Decoupled from the {@link com.nyxn.ecommerce.solid.orders.domain.Order} aggregate. The
 * aggregate exposes typed value objects (e.g. {@code OrderId}, {@code Money}); the response record
 * exposes primitives that serialise cleanly to JSON without custom Jackson configuration. This
 * boundary also prevents the aggregate from growing getters it does not need for business logic.
 */
public record OrderResponse(
    UUID id,
    String customerId,
    UUID productId,
    int quantity,
    BigDecimal amount,
    String currency,
    String status,
    String paymentReference,
    Instant createdAt) {}

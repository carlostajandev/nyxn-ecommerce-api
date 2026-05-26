package com.nyxn.ecommerce.solid.orders.ports.in;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Command object carrying all data required to place an order.
 *
 * <p>Records are immutable by construction — no defensive copies needed. Bean Validation
 * annotations guard the HTTP contract; deeper business invariants (e.g. negative stock) are
 * enforced inside the domain aggregates and value objects.
 *
 * <p>Using a dedicated command record instead of raw parameters makes the call site self-
 * documenting ({@code new PlaceOrderCommand(...)}) and easy to extend: adding an optional field
 * (e.g. a coupon code) is a one-line change with no method-signature ripple.
 */
public record PlaceOrderCommand(
    @NotBlank(message = "customerId is required") String customerId,
    @NotNull(message = "productId is required") UUID productId,
    @Min(value = 1, message = "quantity must be at least 1") int quantity,
    @NotBlank(message = "paymentMethod is required") String paymentMethod) {}

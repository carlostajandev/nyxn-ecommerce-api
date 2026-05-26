package com.nyxn.ecommerce.application.dto;

import jakarta.validation.constraints.Min;

/**
 * Request payload for stock reservation and release operations.
 *
 * <p>The {@code quantity} minimum is 1 — a zero-unit reservation is a no-op that would still
 * consume Redis and DB resources. The controller validates this record before forwarding to the use
 * case; the domain additionally enforces non-negative stock invariants inside value objects.
 */
public record StockReservationRequest(
    @Min(value = 1, message = "quantity must be at least 1") int quantity) {}

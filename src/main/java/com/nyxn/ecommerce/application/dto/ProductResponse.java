package com.nyxn.ecommerce.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * HTTP response payload for a product.
 *
 * <p>Keeping this separate from the domain aggregate means the internal model can evolve freely
 * without breaking the API contract. It also prevents accidentally leaking internal fields (audit
 * metadata, version counters) into the public response.
 */
public record ProductResponse(
    UUID id,
    String name,
    String description,
    BigDecimal price,
    String currency,
    int stock,
    String category,
    Instant createdAt) {}

package com.nyxn.ecommerce.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO de respuesta: lo que el cliente ve. Nunca expone la entidad JPA ni el agregado de dominio.
 * Esto permite evolucionar el modelo de dominio sin romper el contrato HTTP.
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

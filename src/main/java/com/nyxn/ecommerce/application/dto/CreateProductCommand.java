package com.nyxn.ecommerce.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Command object: datos de entrada para crear un producto. Lleva las anotaciones de Bean
 * Validation. No es el dominio — es el contrato HTTP desacoplado del modelo de negocio.
 */
public record CreateProductCommand(
    @NotBlank(message = "Name is required")
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
        String name,
    @NotBlank(message = "Description is required")
        @Size(max = 500, message = "Description cannot exceed 500 characters")
        String description,
    @NotNull(message = "Price is required")
        @DecimalMin(value = "0.01", message = "Price must be greater than 0")
        BigDecimal price,
    @NotNull(message = "Stock is required") @Min(value = 0, message = "Stock cannot be negative")
        Integer stock,
    @NotBlank(message = "Category is required")
        @Size(min = 2, max = 50, message = "Category must be between 2 and 50 characters")
        String category) {}

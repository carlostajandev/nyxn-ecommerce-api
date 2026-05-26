package com.nyxn.ecommerce.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Input command for product creation.
 *
 * <p>Bean Validation annotations live here to guard the HTTP contract (format, presence, range).
 * Business-level rules (negative price, invalid stock) are enforced inside the domain value
 * objects, not here.
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

package com.nyxn.ecommerce.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Request body for creating a new product in the catalog")
public record CreateProductCommand(
    @Schema(description = "Product display name", example = "Laptop Pro 15", minLength = 2, maxLength = 100)
        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
        String name,

    @Schema(description = "Detailed product description", example = "High-performance laptop with 16GB RAM, 512GB NVMe SSD, and 10-hour battery life", maxLength = 500)
        @NotBlank(message = "Description is required")
        @Size(max = 500, message = "Description cannot exceed 500 characters")
        String description,

    @Schema(description = "Unit price in USD (must be > 0)", example = "1299.99", minimum = "0.01")
        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.01", message = "Price must be greater than 0")
        BigDecimal price,

    @Schema(description = "Available units in inventory (0 = out of stock)", example = "50", minimum = "0")
        @NotNull(message = "Stock is required")
        @Min(value = 0, message = "Stock cannot be negative")
        Integer stock,

    @Schema(description = "Product category. Drives analytics grouping and low-stock alerting", example = "ELECTRONICS", minLength = 2, maxLength = 50)
        @NotBlank(message = "Category is required")
        @Size(min = 2, max = 50, message = "Category must be between 2 and 50 characters")
        String category) {}

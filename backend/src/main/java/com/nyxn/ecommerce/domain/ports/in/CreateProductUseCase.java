package com.nyxn.ecommerce.domain.ports.in;

import com.nyxn.ecommerce.application.dto.CreateProductCommand;
import com.nyxn.ecommerce.domain.model.Product;

/**
 * Inbound port: contract for the create-product use case.
 *
 * <p>The controller depends on this interface, never on the concrete implementation. This allows
 * decorating the use case (logging, metrics, circuit breaker) without touching the controller.
 */
public interface CreateProductUseCase {

  Product execute(CreateProductCommand command);
}

package com.nyxn.ecommerce.domain.ports.in;

import com.nyxn.ecommerce.application.dto.CreateProductCommand;
import com.nyxn.ecommerce.domain.model.Product;

/**
 * Puerto de entrada: contrato que el mundo exterior usa para crear un producto. La implementación
 * vive en application/usecases. El controller conoce este puerto, no la implementación concreta.
 */
public interface CreateProductUseCase {

  Product execute(CreateProductCommand command);
}

package com.nyxn.ecommerce.domain.ports.out;

import com.nyxn.ecommerce.domain.model.Product;

/**
 * Puerto de salida: el dominio declara que quiere publicar eventos. GCP Pub/Sub es el adaptador —
 * el dominio no sabe ni le importa que existe.
 */
public interface ProductEventPublisher {

  void publishProductCreated(Product product);

  void publishProductUpdated(Product product);

  void publishProductDeleted(String productId);
}

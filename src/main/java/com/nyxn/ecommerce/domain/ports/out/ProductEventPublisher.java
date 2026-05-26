package com.nyxn.ecommerce.domain.ports.out;

import com.nyxn.ecommerce.domain.model.Product;

/**
 * Outbound port: contract for publishing product domain events.
 *
 * <p>The domain doesn't know whether events go to GCP Pub/Sub, Kafka, or an in-memory bus.
 * Switching brokers only requires a new adapter implementation.
 */
public interface ProductEventPublisher {

  void publishProductCreated(Product product);

  void publishProductUpdated(Product product);

  void publishProductDeleted(String productId);
}

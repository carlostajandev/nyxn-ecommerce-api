package com.nyxn.ecommerce.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyxn.ecommerce.domain.model.Product;
import com.nyxn.ecommerce.domain.ports.out.ProductEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gcp.pubsub.core.PubSubTemplate;
import org.springframework.stereotype.Component;

/**
 * Adaptador secundario: implementa ProductEventPublisher usando GCP Pub/Sub. El dominio no sabe que
 * GCP existe. Si manana migras a Kafka, solo cambias este adaptador, el dominio no se toca.
 */
@Component
public class PubSubProductEventPublisher implements ProductEventPublisher {

  private static final Logger log = LoggerFactory.getLogger(PubSubProductEventPublisher.class);

  private final PubSubTemplate pubSubTemplate;
  private final ObjectMapper objectMapper;

  @Value("${nyxn.pubsub.topics.product-events}")
  private String productEventsTopic;

  public PubSubProductEventPublisher(PubSubTemplate pubSubTemplate, ObjectMapper objectMapper) {
    this.pubSubTemplate = pubSubTemplate;
    this.objectMapper = objectMapper;
  }

  @Override
  public void publishProductCreated(Product product) {
    publish("PRODUCT_CREATED", product.getId().getValue().toString(), product);
  }

  @Override
  public void publishProductUpdated(Product product) {
    publish("PRODUCT_UPDATED", product.getId().getValue().toString(), product);
  }

  @Override
  public void publishProductDeleted(String productId) {
    publish("PRODUCT_DELETED", productId, null);
  }

  private void publish(String eventType, String entityId, Object payload) {
    try {
      ProductEvent event = new ProductEvent(eventType, entityId, payload);
      String message = objectMapper.writeValueAsString(event);
      pubSubTemplate
          .publish(productEventsTopic, message)
          .addCallback(
              id -> log.info("Event {} published for entity {}", eventType, entityId),
              ex -> log.error("Failed to publish event {} for entity {}", eventType, entityId, ex));
    } catch (JsonProcessingException ex) {
      log.error("Failed to serialize event {} for entity {}", eventType, entityId, ex);
    }
  }

  public record ProductEvent(String eventType, String entityId, Object payload) {}
}

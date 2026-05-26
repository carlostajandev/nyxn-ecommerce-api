package com.nyxn.ecommerce.solid.orders.infrastructure.messaging;

import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.nyxn.ecommerce.solid.orders.domain.Order;
import com.nyxn.ecommerce.solid.orders.ports.out.OrderEventPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * GCP Pub/Sub implementation of {@link OrderEventPort}.
 *
 * <p>SRP fix: all Pub/Sub concerns — topic names, message serialisation, retry configuration — live
 * here. The application service never mentions Pub/Sub; it calls {@code
 * orderEventPort.publishOrderPlaced(order)} and this adapter handles the rest.
 *
 * <p>DIP fix: the application service holds a reference typed as {@link OrderEventPort}. If we
 * switch from Pub/Sub to Kafka, we write a new adapter — no application-layer code changes.
 *
 * <p>Note on delivery guarantees: this adapter publishes after the database transaction commits. A
 * crash between commit and publish loses the event. The Outbox Pattern — persist the event in the
 * same transaction, relay asynchronously — eliminates that window at the cost of a relay process.
 * For this demonstration, at-most-once delivery is acceptable.
 */
@Component
public class PubSubOrderEventAdapter implements OrderEventPort {

  private static final Logger log = LoggerFactory.getLogger(PubSubOrderEventAdapter.class);

  private final PubSubTemplate pubSubTemplate;
  private final String topic;

  public PubSubOrderEventAdapter(
      PubSubTemplate pubSubTemplate,
      @Value("${nyxn.pubsub.topics.order-events:order-events}") String topic) {
    this.pubSubTemplate = pubSubTemplate;
    this.topic = topic;
  }

  @Override
  public void publishOrderPlaced(Order order) {
    // A production payload would be a typed DTO serialised to JSON, including a schema version
    // so consumers can evolve independently. Bare string concatenation is used here for clarity.
    String payload =
        "{\"orderId\":\"%s\",\"customerId\":\"%s\",\"status\":\"%s\"}"
            .formatted(order.getId(), order.getCustomerId(), order.getStatus());

    pubSubTemplate
        .publish(topic, payload)
        .thenAccept(
            msgId -> log.info("order-placed event {} published for {}", msgId, order.getId()))
        .exceptionally(
            ex -> {
              log.error("Failed to publish order-placed event for {}", order.getId(), ex);
              return null;
            });
  }
}

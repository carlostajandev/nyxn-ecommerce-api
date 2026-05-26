package com.nyxn.ecommerce.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.cloud.spring.pubsub.support.BasicAcknowledgeablePubsubMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * GCP Pub/Sub subscriber for the {@code product-events} subscription.
 *
 * <h2>Ack / Nack strategy</h2>
 *
 * <ul>
 *   <li><b>Ack</b>: message processed successfully — Pub/Sub removes it from the subscription.
 *   <li><b>Nack</b>: processing failed — Pub/Sub redelivers the message up to the subscription's
 *       {@code maxDeliveryAttempts} limit (configured as 5 in docker-compose pubsub-init). After 5
 *       consecutive nacks the emulator (or GCP) routes the message to the dead-letter topic ({@code
 *       product-events-dlq}), preventing a poison-pill message from blocking the subscription
 *       indefinitely.
 * </ul>
 *
 * <h2>Why {@code @PostConstruct} + {@code @PreDestroy}?</h2>
 *
 * <p>{@code @PostConstruct} runs after all Spring beans are wired and the application context is
 * fully initialized — safe to start consuming messages at that point. {@code @PreDestroy} runs
 * before the context closes, ensuring the subscriber drains in-flight messages and stops cleanly
 * rather than dropping them mid-processing during a rolling deploy.
 *
 * <h2>Threading</h2>
 *
 * <p>The Pub/Sub client library manages its own gRPC thread pool. The handler lambda is called on
 * those threads — it must be thread-safe. {@link ObjectMapper} is thread-safe; {@link
 * ProductEventRouter} methods must also be thread-safe (see that class's Javadoc). If a handler
 * blocks (e.g. waiting on a DB write), the library will buffer messages and apply back-pressure
 * automatically up to the configured {@code maxOutstandingMessages} limit.
 *
 * <h2>Production considerations</h2>
 *
 * <ul>
 *   <li>Add {@code flow-control.max-outstanding-element-count} to tune back-pressure per
 *       subscriber.
 *   <li>Use a separate thread pool via {@code SubscriberStubSettings} if handler latency is high.
 *   <li>Instrument with Micrometer counters: messages processed, acked, nacked, DLQ routed.
 *   <li>In GCP production, configure a DLQ subscription so an operator can inspect failed messages
 *       and replay them after the bug causing nacks is fixed.
 * </ul>
 */
@Component
public class ProductEventSubscriber {

  private static final Logger log = LoggerFactory.getLogger(ProductEventSubscriber.class);

  private final PubSubTemplate pubSubTemplate;
  private final ObjectMapper objectMapper;
  private final String subscription;

  // Holds the subscriber handle returned by PubSubTemplate so we can stop it on shutdown.
  // Volatile: read by the PreDestroy thread, written by the PostConstruct thread.
  private volatile Subscriber activeSubscriber;

  public ProductEventSubscriber(
      PubSubTemplate pubSubTemplate,
      ObjectMapper objectMapper,
      @Value("${nyxn.pubsub.subscriptions.product-events:product-events-subscription}")
          String subscription) {
    this.pubSubTemplate = pubSubTemplate;
    this.objectMapper = objectMapper;
    this.subscription = subscription;
  }

  @PostConstruct
  public void startSubscription() {
    log.info("Starting Pub/Sub subscription on '{}'", subscription);

    // PubSubTemplate.subscribe returns a Subscriber handle that runs on the Pub/Sub
    // client library's internal thread pool — non-blocking for the application.
    // PubSubTemplate.subscribe() returns com.google.cloud.pubsub.v1.Subscriber —
    // the same type used by the underlying Google Cloud client library.
    activeSubscriber =
        pubSubTemplate.subscribe(
            subscription,
            message -> {
              String messageId = message.getPubsubMessage().getMessageId();
              try {
                handleMessage(message);
                message.ack();
                log.debug("Message {} acked", messageId);
              } catch (Exception e) {
                // Nack triggers redelivery. After maxDeliveryAttempts (5) consecutive
                // nacks, GCP routes the message to the dead-letter topic automatically.
                message.nack();
                log.error("Message {} nacked — will be retried or sent to DLQ", messageId, e);
              }
            });
  }

  @PreDestroy
  public void stopSubscription() {
    if (activeSubscriber != null) {
      log.info("Stopping Pub/Sub subscription on '{}'", subscription);
      // stopAsync initiates a graceful shutdown: stops accepting new messages and waits
      // for in-flight handlers to complete before closing the gRPC channel.
      activeSubscriber.stopAsync();
    }
  }

  // ─── Message dispatch ──────────────────────────────────────────────────────

  private void handleMessage(BasicAcknowledgeablePubsubMessage raw) throws Exception {
    String payload = raw.getPubsubMessage().getData().toStringUtf8();
    String messageId = raw.getPubsubMessage().getMessageId();
    log.debug("Processing message {}: {}", messageId, payload);

    // Deserialise to the same envelope structure used by PubSubProductEventPublisher.
    // Any JSON parse error throws and triggers a nack → redelivery → eventually DLQ.
    ProductEventEnvelope envelope = objectMapper.readValue(payload, ProductEventEnvelope.class);

    switch (envelope.eventType()) {
      case "PRODUCT_CREATED" ->
          log.info(
              "Product created event received: entityId={}, messageId={}",
              envelope.entityId(),
              messageId);
      case "PRODUCT_UPDATED" ->
          log.info(
              "Product updated event received: entityId={}, messageId={}",
              envelope.entityId(),
              messageId);
      case "PRODUCT_DELETED" ->
          log.info(
              "Product deleted event received: entityId={}, messageId={}",
              envelope.entityId(),
              messageId);
      default ->
          // Unknown event types are logged and acked — not nacked — because nacking
          // an unknown type would spin it through redelivery forever until the DLQ.
          // An unknown type is a producer-side bug, not a transient processing failure.
          log.warn(
              "Unknown event type '{}' in message {} — acking to prevent DLQ spin",
              envelope.eventType(),
              messageId);
    }
  }

  // ─── Envelope DTO ─────────────────────────────────────────────────────────
  // Must match the structure published by PubSubProductEventPublisher.ProductEvent.
  // A production system would share this record via a shared events library or
  // an Avro/Protobuf schema to guarantee producer-consumer contract alignment.
  public record ProductEventEnvelope(String eventType, String entityId, Object payload) {}
}

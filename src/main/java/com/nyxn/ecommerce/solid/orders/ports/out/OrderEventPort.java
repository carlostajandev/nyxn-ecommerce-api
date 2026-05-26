package com.nyxn.ecommerce.solid.orders.ports.out;

import com.nyxn.ecommerce.solid.orders.domain.Order;

/**
 * Outbound port: domain event publishing.
 *
 * <p>DIP fix: the application service needs to communicate that an order was placed — it does not
 * need to know whether that communication travels over GCP Pub/Sub, Kafka, RabbitMQ, or an
 * in-memory bus. This interface makes the intent explicit and the mechanism swappable.
 *
 * <p>SRP fix: event serialisation, topic routing, and retry logic are exclusively in the adapter.
 * If the message format changes, the application service is untouched.
 *
 * <p>Production note: publishing after the database commit introduces an at-most-once delivery
 * window — if the publisher fails after the commit, the event is lost. The correct production
 * pattern is the Outbox Pattern: persist the event in the same transaction as the order, then relay
 * it asynchronously. That pattern is out of scope for this SOLID demonstration.
 */
public interface OrderEventPort {

  /**
   * Publishes a domain event signalling that an order was placed.
   *
   * @param order the confirmed order aggregate
   */
  void publishOrderPlaced(Order order);
}

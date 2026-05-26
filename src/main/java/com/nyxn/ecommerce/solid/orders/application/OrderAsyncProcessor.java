package com.nyxn.ecommerce.solid.orders.application;

import com.nyxn.ecommerce.solid.orders.domain.Order;
import com.nyxn.ecommerce.solid.orders.ports.out.NotificationPort;
import com.nyxn.ecommerce.solid.orders.ports.out.OrderEventPort;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Runs post-order tasks (notification + event) asynchronously after the transaction commits.
 *
 * <p>This class exists because of a fundamental Spring AOP constraint: {@code @Async} works by
 * wrapping the bean in a proxy that intercepts method calls from <em>outside</em> the bean. When
 * {@link PlaceOrderService#execute} calls a method on {@code this}, it bypasses the proxy entirely
 * — the {@code @Async} annotation is silently ignored and the call runs synchronously on the same
 * thread.
 *
 * <p>Extracting the async tasks to a separate Spring-managed bean forces every call to go through
 * the proxy, making {@code @Async} effective. {@link PlaceOrderService} injects this component and
 * calls it after the transaction commits — the async dispatch then happens correctly on the task
 * executor.
 */
@Component
public class OrderAsyncProcessor {

  private static final Logger log = LoggerFactory.getLogger(OrderAsyncProcessor.class);

  private final NotificationPort notificationPort;
  private final OrderEventPort orderEventPort;

  public OrderAsyncProcessor(NotificationPort notificationPort, OrderEventPort orderEventPort) {
    this.notificationPort = notificationPort;
    this.orderEventPort = orderEventPort;
  }

  /**
   * Sends the order confirmation and publishes the domain event on a background thread.
   *
   * <p>{@code CompletableFuture.allOf} lets both tasks run concurrently on the executor's thread
   * pool rather than sequentially. A failure in either task is caught and logged — it must not
   * propagate back to the caller because the order is already committed and the HTTP response has
   * been returned.
   *
   * @param order the confirmed, persisted order
   * @return a future that completes when both tasks finish (used by callers that need to await)
   */
  @Async
  public CompletableFuture<Void> dispatchPostOrderTasks(Order order) {
    CompletableFuture<Void> notif =
        CompletableFuture.runAsync(
            () -> {
              try {
                notificationPort.sendOrderConfirmation(order);
              } catch (Exception e) {
                // Notification failure must not surface as an HTTP error — the order is confirmed.
                // A dead-letter queue or retry scheduler would pick this up in production.
                log.error("Notification failed for order {}", order.getId(), e);
              }
            });

    CompletableFuture<Void> event =
        CompletableFuture.runAsync(
            () -> {
              try {
                orderEventPort.publishOrderPlaced(order);
              } catch (Exception e) {
                log.error("Event publish failed for order {}", order.getId(), e);
              }
            });

    return CompletableFuture.allOf(notif, event)
        .thenRun(() -> log.debug("Post-order tasks complete for {}", order.getId()));
  }
}

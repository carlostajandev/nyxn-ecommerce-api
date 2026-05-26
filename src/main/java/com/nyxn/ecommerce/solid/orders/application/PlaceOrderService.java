package com.nyxn.ecommerce.solid.orders.application;

import com.nyxn.ecommerce.domain.valueobject.Money;
import com.nyxn.ecommerce.domain.valueobject.ProductId;
import com.nyxn.ecommerce.solid.orders.domain.Order;
import com.nyxn.ecommerce.solid.orders.domain.OrderId;
import com.nyxn.ecommerce.solid.orders.ports.in.OrderReportUseCase;
import com.nyxn.ecommerce.solid.orders.ports.in.PlaceOrderCommand;
import com.nyxn.ecommerce.solid.orders.ports.in.PlaceOrderUseCase;
import com.nyxn.ecommerce.solid.orders.ports.out.InventoryPort;
import com.nyxn.ecommerce.solid.orders.ports.out.NotificationPort;
import com.nyxn.ecommerce.solid.orders.ports.out.OrderEventPort;
import com.nyxn.ecommerce.solid.orders.ports.out.OrderPersistencePort;
import com.nyxn.ecommerce.solid.orders.ports.out.PaymentPort;
import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service that orchestrates the order-placement flow.
 *
 * <p>This class is the SOLID-compliant replacement for {@link
 * com.nyxn.ecommerce.solid.legacy.LegacyOrderService}. The changes below address each violation:
 *
 * <h2>SRP — Single Responsibility Principle</h2>
 *
 * This class has exactly one reason to change: the <em>order-placement workflow</em>. Payment
 * mechanics, SMTP configuration, SQL schemas, and event serialisation are concerns owned by their
 * respective adapters. If any adapter changes, this class does not change.
 *
 * <h2>OCP — Open/Closed Principle</h2>
 *
 * Adding a new payment provider (e.g. Apple Pay) requires writing a new {@link PaymentPort} adapter
 * and registering it in the DI container — this class is untouched. The legacy service required a
 * new {@code else if} branch here for every provider.
 *
 * <h2>LSP — Liskov Substitution Principle</h2>
 *
 * This class does not expose an override point that can weaken its contracts. The stock check is a
 * method-level guard ({@code if (!inventory.isAvailable(...))}) that cannot be silently bypassed by
 * a subclass the way {@code DiscountedOrderService} bypassed it in the legacy version. A future
 * subclass would have to actively defeat the guard — and a code reviewer would see it immediately.
 *
 * <h2>ISP — Interface Segregation Principle</h2>
 *
 * This class implements two narrow interfaces: {@link PlaceOrderUseCase} for order placement and
 * {@link OrderReportUseCase} for reporting. A controller that only places orders injects {@link
 * PlaceOrderUseCase} and never sees the reporting API. Tests for order placement mock only {@link
 * PlaceOrderUseCase} — no dead stubs required.
 *
 * <h2>DIP — Dependency Inversion Principle</h2>
 *
 * All dependencies are injected via constructor as interfaces. The class never mentions {@code new
 * StripeGateway()} or {@code new SmtpClient()}. Swapping any adapter — for a test double, a shadow
 * deployment, or a technology migration — does not require touching this file.
 */
@Service
public class PlaceOrderService implements PlaceOrderUseCase, OrderReportUseCase {

  private static final Logger log = LoggerFactory.getLogger(PlaceOrderService.class);

  // All dependencies are interfaces — the DI container injects concrete adapters at startup.
  // Constructor injection (not field injection) makes dependencies visible and mandatory,
  // and allows tests to pass stubs without a Spring context.
  private final PaymentPort paymentPort;
  private final InventoryPort inventoryPort;
  private final NotificationPort notificationPort;
  private final OrderPersistencePort orderPersistencePort;
  private final OrderEventPort orderEventPort;

  // AtomicInteger instead of a static int: thread-safe without synchronisation, relevant because
  // @Async below means multiple threads may increment concurrently.
  private final AtomicInteger totalOrdersProcessed = new AtomicInteger(0);

  public PlaceOrderService(
      PaymentPort paymentPort,
      InventoryPort inventoryPort,
      NotificationPort notificationPort,
      OrderPersistencePort orderPersistencePort,
      OrderEventPort orderEventPort) {
    this.paymentPort = paymentPort;
    this.inventoryPort = inventoryPort;
    this.notificationPort = notificationPort;
    this.orderPersistencePort = orderPersistencePort;
    this.orderEventPort = orderEventPort;
  }

  /**
   * Places a new order.
   *
   * <p>The workflow is intentionally sequential up to persistence — payment and stock reservation
   * must succeed before we commit the order. Notification and event publishing are decoupled: they
   * run asynchronously after the transaction commits, so a transient e-mail failure does not roll
   * back a settled payment.
   *
   * <p>Step sequence:
   *
   * <ol>
   *   <li>Validate stock availability — fail fast before charging the customer.
   *   <li>Calculate total with applicable discount.
   *   <li>Capture payment via the payment port.
   *   <li>Reserve inventory.
   *   <li>Persist the confirmed order.
   *   <li>Trigger async notification and domain event (fire-and-forget after commit).
   * </ol>
   */
  @Override
  @Transactional
  public Order execute(PlaceOrderCommand command) {
    ProductId productId = ProductId.of(command.productId());

    // Guard: check stock before charging. If payment succeeded but stock is gone,
    // the customer is charged with no fulfilment — avoid that by checking first.
    if (!inventoryPort.isAvailable(productId, command.quantity())) {
      throw new InsufficientOrderStockException(
          "Product %s does not have enough stock for quantity %d"
              .formatted(command.productId(), command.quantity()));
    }

    Money total = calculateTotal(command.quantity());

    // Payment captured synchronously — we need the reference on the aggregate
    // before we can persist the order.
    String paymentRef = paymentPort.charge(command.customerId(), total.getAmount());

    Order order =
        Order.builder()
            .id(OrderId.generate())
            .customerId(command.customerId())
            .productId(productId)
            .quantity(command.quantity())
            .total(total)
            .build();

    order.confirmPayment(paymentRef);

    inventoryPort.deduct(productId, command.quantity());
    order.reserveStock();
    order.confirm();

    Order saved = orderPersistencePort.save(order);
    totalOrdersProcessed.incrementAndGet();

    // Fire-and-forget: notification and event publishing are decoupled from the transaction.
    // The order is committed before these run. If they fail, the order still exists and can
    // be retried by a background job. This trades strict consistency for user-facing latency.
    notifyAndPublishAsync(saved);

    log.info("Order {} placed for customer {}", saved.getId(), saved.getCustomerId());
    return saved;
  }

  @Override
  public String generateReport() {
    // ISP in practice: this method lives on a separate interface. A caller that only
    // needs PlaceOrderUseCase cannot reach this method — it is invisible to them.
    return "Total orders processed: " + totalOrdersProcessed.get();
  }

  // ─── Private helpers ────────────────────────────────────────────────────────

  /**
   * Calculates the order total, applying a bulk discount when applicable.
   *
   * <p>Extracting the pricing rule into its own method satisfies SRP at the method level: if the
   * discount tiers change, there is exactly one place to update. The alternative — inlining this in
   * {@code execute()} — mixes pricing rules with orchestration logic, making both harder to read
   * and test.
   */
  private Money calculateTotal(int quantity) {
    // Hard-coded price is a placeholder — a real implementation would call a ProductPort.
    BigDecimal unitPrice = new BigDecimal("99.99");
    BigDecimal raw = unitPrice.multiply(BigDecimal.valueOf(quantity));
    // Apply a 10 % bulk discount for orders of more than 10 units.
    if (quantity > 10) {
      raw = raw.multiply(new BigDecimal("0.90"));
    }
    return Money.ofUSD(raw);
  }

  /**
   * Asynchronously notifies the customer and publishes the domain event.
   *
   * <p>{@code @Async} tells Spring to run this method on a separate thread from the task executor,
   * allowing the HTTP response to return as soon as the order is persisted — the customer does not
   * wait for SMTP or Pub/Sub. {@code CompletableFuture.allOf} lets us join both tasks and log a
   * single summary instead of writing two separate try/catch blocks.
   *
   * <p>Trade-off: if the application crashes between the commit and these calls, the notification
   * and event are lost. The Outbox Pattern eliminates that gap at the cost of added infrastructure.
   */
  @Async
  public CompletableFuture<Void> notifyAndPublishAsync(Order order) {
    CompletableFuture<Void> notif =
        CompletableFuture.runAsync(
            () -> {
              try {
                notificationPort.sendOrderConfirmation(order);
              } catch (Exception e) {
                log.error("Failed to send notification for order {}", order.getId(), e);
              }
            });

    CompletableFuture<Void> event =
        CompletableFuture.runAsync(
            () -> {
              try {
                orderEventPort.publishOrderPlaced(order);
              } catch (Exception e) {
                log.error("Failed to publish event for order {}", order.getId(), e);
              }
            });

    return CompletableFuture.allOf(notif, event)
        .thenRun(() -> log.debug("Async post-order tasks completed for {}", order.getId()));
  }
}

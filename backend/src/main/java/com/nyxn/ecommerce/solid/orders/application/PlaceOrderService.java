package com.nyxn.ecommerce.solid.orders.application;

import com.nyxn.ecommerce.domain.valueobject.Money;
import com.nyxn.ecommerce.domain.valueobject.ProductId;
import com.nyxn.ecommerce.solid.orders.domain.Order;
import com.nyxn.ecommerce.solid.orders.domain.OrderId;
import com.nyxn.ecommerce.solid.orders.ports.in.OrderReportUseCase;
import com.nyxn.ecommerce.solid.orders.ports.in.PlaceOrderCommand;
import com.nyxn.ecommerce.solid.orders.ports.in.PlaceOrderUseCase;
import com.nyxn.ecommerce.solid.orders.ports.out.InventoryPort;
import com.nyxn.ecommerce.solid.orders.ports.out.OrderPersistencePort;
import com.nyxn.ecommerce.solid.orders.ports.out.PaymentPort;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service that orchestrates the order-placement flow.
 *
 * <p>This class is the SOLID-compliant replacement for {@link
 * com.nyxn.ecommerce.solid.legacy.LegacyOrderService}. Each SOLID principle is addressed:
 *
 * <h2>SRP — Single Responsibility Principle</h2>
 *
 * One reason to change: the order-placement workflow. Payment mechanics, SMTP configuration, SQL
 * schemas, and event serialisation are concerns owned by their respective adapters.
 *
 * <h2>OCP — Open/Closed Principle</h2>
 *
 * Adding a new payment provider requires writing a new {@link PaymentPort} adapter — this class is
 * untouched. No {@code else if} branches here.
 *
 * <h2>LSP — Liskov Substitution Principle</h2>
 *
 * The stock check is a method-level guard that cannot be silently bypassed by a subclass, unlike
 * {@code DiscountedOrderService} in the legacy design.
 *
 * <h2>ISP — Interface Segregation Principle</h2>
 *
 * Implements two narrow interfaces: {@link PlaceOrderUseCase} for placement and {@link
 * OrderReportUseCase} for reporting. A controller that only places orders sees neither.
 *
 * <h2>DIP — Dependency Inversion Principle</h2>
 *
 * All dependencies are constructor-injected interfaces. No {@code new ConcreteAdapter()} anywhere.
 */
@Service
public class PlaceOrderService implements PlaceOrderUseCase, OrderReportUseCase {

  private static final Logger log = LoggerFactory.getLogger(PlaceOrderService.class);

  private final PaymentPort paymentPort;
  private final InventoryPort inventoryPort;
  private final OrderPersistencePort orderPersistencePort;
  private final OrderAsyncProcessor asyncProcessor;

  // AtomicInteger because asyncProcessor runs on a background thread — plain int would be a race.
  private final AtomicInteger totalOrdersProcessed = new AtomicInteger(0);

  public PlaceOrderService(
      PaymentPort paymentPort,
      InventoryPort inventoryPort,
      OrderPersistencePort orderPersistencePort,
      OrderAsyncProcessor asyncProcessor) {
    this.paymentPort = paymentPort;
    this.inventoryPort = inventoryPort;
    this.orderPersistencePort = orderPersistencePort;
    this.asyncProcessor = asyncProcessor;
  }

  /**
   * Places a new order.
   *
   * <p>Step sequence:
   *
   * <ol>
   *   <li>Check stock — fail fast before charging the customer.
   *   <li>Calculate total with bulk discount if applicable.
   *   <li>Capture payment via the payment port.
   *   <li>Deduct inventory and advance aggregate state.
   *   <li>Persist the confirmed order inside the transaction.
   *   <li>Trigger async notification and domain event after the commit.
   * </ol>
   *
   * <p>Notification and event publishing run asynchronously in {@link OrderAsyncProcessor} — a
   * transient SMTP or Pub/Sub failure does not roll back a payment that already settled.
   */
  @Override
  @Transactional
  public Order execute(PlaceOrderCommand command) {
    ProductId productId = ProductId.of(command.productId());

    if (!inventoryPort.isAvailable(productId, command.quantity())) {
      throw new InsufficientOrderStockException(
          "Product %s does not have enough stock for quantity %d"
              .formatted(command.productId(), command.quantity()));
    }

    Money total = calculateTotal(command.quantity());
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

    // Dispatch via a separate Spring bean so @Async goes through the proxy and actually runs
    // on the task executor. Calling a @Async method on 'this' bypasses the proxy silently.
    asyncProcessor.dispatchPostOrderTasks(saved);

    log.info("Order {} placed for customer {}", saved.getId(), saved.getCustomerId());
    return saved;
  }

  @Override
  public String generateReport() {
    return "Total orders processed: " + totalOrdersProcessed.get();
  }

  /**
   * Applies bulk discount for orders exceeding ten units.
   *
   * <p>Isolated into its own method so that pricing rule changes have a single, obvious edit point
   * separate from the orchestration logic in {@link #execute}.
   */
  private Money calculateTotal(int quantity) {
    BigDecimal unitPrice = new BigDecimal("99.99");
    BigDecimal raw = unitPrice.multiply(BigDecimal.valueOf(quantity));
    if (quantity > 10) {
      raw = raw.multiply(new BigDecimal("0.90"));
    }
    return Money.ofUSD(raw);
  }
}

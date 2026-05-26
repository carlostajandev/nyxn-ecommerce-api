package com.nyxn.ecommerce.solid.legacy;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ⚠️ THIS CLASS IS INTENTIONALLY BAD CODE — it is the "before" state used to demonstrate SOLID
 * violations. Do not use this class in production. See the {@code orders} package for the corrected
 * implementation.
 *
 * <p>This is a classic "God class": a single class that owns business logic, external I/O,
 * persistence, notification delivery, and reporting. Each responsibility is tightly coupled to all
 * the others, making the class impossible to unit-test in isolation and dangerous to modify without
 * risk of unintended side effects.
 *
 * <h2>Violations catalogued</h2>
 *
 * <ul>
 *   <li><b>SRP</b> (Single Responsibility): at least five different reasons to change — pricing
 *       rules, payment providers, e-mail layout, SQL schema, event format. One class, five axes of
 *       change.
 *   <li><b>OCP</b> (Open/Closed): adding a new payment method requires a new {@code else if} block
 *       inside {@code processOrder}, touching — and potentially breaking — existing payment paths.
 *   <li><b>LSP</b> (Liskov Substitution): {@code DiscountedOrderService} overrides {@code
 *       processOrder} but silently ignores the stock check, breaking the contract callers rely on.
 *   <li><b>ISP</b> (Interface Segregation): {@code OrderOperations} forces every implementor to
 *       provide a reporting method even when the implementor only needs to process orders, coupling
 *       unrelated concerns.
 *   <li><b>DIP</b> (Dependency Inversion): business logic instantiates concrete infrastructure
 *       classes ({@code new SmtpEmailClient()}, {@code new StripeGateway()}) instead of depending
 *       on abstractions, making every adapter swap a core-logic edit.
 * </ul>
 */
@SuppressWarnings({
  "java:S1541", // cyclomatic complexity — intentional, illustrates the cost of SRP violation
  "java:S2696", // static field mutation — intentional, illustrates stateful God-class anti-pattern
  "unused" // fields/methods exist to show the full scope of a God-class
})
public class LegacyOrderService implements OrderOperations {

  private static final Logger log = LoggerFactory.getLogger(LegacyOrderService.class);

  // ─── DIP violation ────────────────────────────────────────────────────────
  // The business method instantiates concrete infrastructure directly.
  // Swapping Stripe for PayPal means editing this class — a dependency that
  // should be inverted so that core logic never mentions concrete adapters.
  private final StripeGateway stripeGateway = new StripeGateway();
  private final SmtpEmailClient emailClient = new SmtpEmailClient();

  // ─── SRP violation ────────────────────────────────────────────────────────
  // The service owns its own in-memory "database". A schema change (e.g. adding
  // an order status column) forces a change here, not in a persistence adapter.
  private final Map<String, Object[]> ordersDb = new HashMap<>();
  private static int totalOrdersProcessed = 0; // mutable global state — hidden coupling

  // ─── ISP violation: forced implementation ─────────────────────────────────
  // OrderOperations bundles processOrder + generateReport in one interface.
  // This implementor only cares about order processing but must carry dead
  // reporting weight. Every mock in tests is forced to stub generateReport too.
  @Override
  public String generateReport() {
    return "Total orders processed: " + totalOrdersProcessed;
  }

  /**
   * Processes a customer order end-to-end.
   *
   * <p>This single method is responsible for: stock validation, discount calculation, payment
   * routing (OCP violation), persistence, e-mail delivery, and event publishing — all serialised in
   * one synchronous call. A failure in e-mail delivery rolls back a payment that already settled.
   *
   * <p>Cyclomatic complexity ≫ 10; impossible to unit-test any path without triggering every other
   * concern.
   *
   * @param customerId customer placing the order
   * @param productId product being ordered
   * @param quantity units requested
   * @param paymentMethod "STRIPE", "PAYPAL", or "CRYPTO" — string switch, not polymorphism
   * @return generated order ID
   */
  @Override
  public String processOrder(
      String customerId, String productId, int quantity, String paymentMethod) {

    log.info("Processing order for customer {}", customerId);

    // ── SRP violation ──────────────────────────────────────────────────────
    // Stock validation is a domain rule; persistence is an infrastructure
    // concern. Both live here, making them inseparable.
    int currentStock = getStockFromDb(productId);
    if (currentStock < quantity) {
      throw new IllegalStateException("Insufficient stock for product: " + productId);
    }

    // ── SRP violation ──────────────────────────────────────────────────────
    // Pricing / discount logic embedded directly in the service.
    // A business decision to change the loyalty tier threshold forces a change
    // to this method, which also owns payment and email code.
    BigDecimal price = getPriceFromDb(productId);
    BigDecimal total = price.multiply(BigDecimal.valueOf(quantity));
    if (quantity > 10) {
      total = total.multiply(new BigDecimal("0.90")); // 10 % bulk discount
    }

    // ── OCP violation ──────────────────────────────────────────────────────
    // Every new payment method adds another else-if here. The class is not
    // closed for modification — it must be opened and edited for each new
    // provider, risking regression in the existing Stripe and PayPal paths.
    String paymentId;
    if ("STRIPE".equals(paymentMethod)) {
      paymentId = stripeGateway.charge(customerId, total);
    } else if ("PAYPAL".equals(paymentMethod)) {
      // DIP violation: PayPalGateway is a concrete class instantiated inline
      paymentId = new PayPalGateway().charge(customerId, total);
    } else if ("CRYPTO".equals(paymentMethod)) {
      // Each new branch is a change to this file — open for modification.
      paymentId = new CryptoGateway().charge(customerId, total);
    } else {
      throw new IllegalArgumentException("Unknown payment method: " + paymentMethod);
    }

    // ── SRP violation ──────────────────────────────────────────────────────
    // Inventory deduction and order persistence are distinct concerns.
    // A database migration (e.g. renaming the "orders" table) requires editing
    // this method that also owns payment routing.
    String orderId = UUID.randomUUID().toString();
    updateStockInDb(productId, currentStock - quantity);
    ordersDb.put(orderId, new Object[] {customerId, productId, quantity, total, paymentId});
    totalOrdersProcessed++;

    // ── SRP + DIP violation ────────────────────────────────────────────────
    // Notification delivery is a completely separate concern.  The e-mail
    // template, subject line, and SMTP host are now reasons for THIS method to
    // change. DIP is violated because SmtpEmailClient is a concrete class —
    // switching to SendGrid requires editing order processing code.
    String emailBody =
        "Dear customer,\n\nYour order "
            + orderId
            + " has been placed.\nTotal: $"
            + total
            + "\n\nThank you!";
    emailClient.send(customerId + "@example.com", "Order Confirmation", emailBody);

    // ── SRP violation ──────────────────────────────────────────────────────
    // Domain event publishing is mixed in. If the message broker is unavailable,
    // the entire order transaction fails even though payment has already settled.
    publishOrderEvent(orderId, customerId, productId, quantity, total);

    log.info("Order {} processed successfully", orderId);
    return orderId;
  }

  // ─── Private helpers — each one is a hidden responsibility ────────────────

  private int getStockFromDb(String productId) {
    // Simulates a JDBC query — coupled directly to the transport, not a port.
    log.debug("Fetching stock for product {}", productId);
    return 50; // placeholder
  }

  private BigDecimal getPriceFromDb(String productId) {
    log.debug("Fetching price for product {}", productId);
    return new BigDecimal("99.99"); // placeholder
  }

  private void updateStockInDb(String productId, int newStock) {
    log.debug("Updating stock for product {} to {}", productId, newStock);
    // Would execute a raw UPDATE statement — no abstraction, no testability.
  }

  private void publishOrderEvent(
      String orderId, String customerId, String productId, int qty, BigDecimal total) {
    // Serialises and pushes to a hard-coded topic — no interface, no testability.
    log.debug(
        "Publishing event for order {} customer {} product {} qty {} total {}",
        orderId,
        customerId,
        productId,
        qty,
        total);
  }

  // ─── LSP violation: subclass breaks the parent contract ───────────────────

  /**
   * ⚠️ Also intentionally bad — shows an LSP violation.
   *
   * <p>Callers of {@link LegacyOrderService#processOrder} reasonably expect that stock is checked
   * before any payment is charged. {@code DiscountedOrderService} silently removes that check. A
   * polymorphic caller that substitutes this subclass will process orders against zero stock —
   * violating Liskov's substitutability principle.
   */
  static class DiscountedOrderService extends LegacyOrderService {

    /**
     * Overrides the parent's contract by skipping the stock guard. The parent's Javadoc promises an
     * {@code IllegalStateException} on insufficient stock; this override makes that promise a lie
     * for callers holding a reference typed as {@code LegacyOrderService}.
     */
    @Override
    public String processOrder(
        String customerId, String productId, int quantity, String paymentMethod) {
      // ── LSP violation ────────────────────────────────────────────────────
      // Silently drops the stock check. Any caller that typed its variable as
      // LegacyOrderService and later receives a DiscountedOrderService will
      // observe different — and dangerous — behaviour without warning.
      log.info("Discounted order skipping stock validation — LSP violation");
      return super.processOrder(customerId, productId, quantity, paymentMethod);
    }
  }

  // ─── Stub adapters (concrete classes — the DIP-violation targets) ─────────

  /** Concrete Stripe adapter — hardwired into business logic via {@code new StripeGateway()}. */
  static class StripeGateway {
    String charge(String customerId, BigDecimal amount) {
      return "stripe_" + UUID.randomUUID();
    }
  }

  /** Adding this class required no interface — DIP missing from day one. */
  static class PayPalGateway {
    String charge(String customerId, BigDecimal amount) {
      return "paypal_" + UUID.randomUUID();
    }
  }

  /** Third concrete adapter, added with another else-if — the OCP tax accumulates. */
  static class CryptoGateway {
    String charge(String customerId, BigDecimal amount) {
      return "crypto_" + UUID.randomUUID();
    }
  }

  /** Concrete SMTP client — swapping to SendGrid requires editing {@code processOrder}. */
  static class SmtpEmailClient {
    void send(String to, String subject, String body) {
      log.debug("Sending email to {}: {}", to, subject);
    }
  }
}

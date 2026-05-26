package com.nyxn.ecommerce.solid.orders.ports.out;

import com.nyxn.ecommerce.solid.orders.domain.Order;

/**
 * Outbound port: customer notification delivery.
 *
 * <p>DIP fix: the application service knows it must notify the customer after a successful order —
 * that is a business rule. It does not know whether that notification is an e-mail, an SMS, or a
 * push notification — that is an infrastructure decision. This port expresses the business intent;
 * the adapter ({@link
 * com.nyxn.ecommerce.solid.orders.infrastructure.email.SmtpNotificationAdapter}) handles the
 * delivery mechanism.
 *
 * <p>SRP fix: the SMTP host, template rendering, and retry logic now live in the adapter, not in
 * the order service. Changing from SMTP to SendGrid requires replacing or adding an adapter — the
 * application service does not change.
 */
public interface NotificationPort {

  /**
   * Sends an order-confirmation notification to the customer.
   *
   * <p>Implementations are expected to be idempotent where possible — duplicate notifications are
   * preferable to missing ones from a customer-experience perspective.
   *
   * @param order the confirmed order to communicate to the customer
   */
  void sendOrderConfirmation(Order order);
}

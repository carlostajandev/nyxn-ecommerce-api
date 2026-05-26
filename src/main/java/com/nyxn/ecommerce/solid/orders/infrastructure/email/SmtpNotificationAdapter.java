package com.nyxn.ecommerce.solid.orders.infrastructure.email;

import com.nyxn.ecommerce.solid.orders.domain.Order;
import com.nyxn.ecommerce.solid.orders.ports.out.NotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * SMTP implementation of {@link NotificationPort}.
 *
 * <p>SRP fix: this class owns exactly one concern — assembling and delivering an order-confirmation
 * e-mail via SMTP. Template content, subject line, and SMTP credentials live here, not in the order
 * service. If the marketing team wants to change the e-mail copy, this is the only class that
 * changes.
 *
 * <p>DIP fix: the application service holds a reference typed as {@link NotificationPort}. It never
 * mentions SMTP, JavaMail, or SendGrid. Swapping the delivery channel (e.g. to an SMS gateway)
 * means writing a new adapter and updating the DI binding — the application service is untouched.
 */
@Component
public class SmtpNotificationAdapter implements NotificationPort {

  private static final Logger log = LoggerFactory.getLogger(SmtpNotificationAdapter.class);

  @Override
  public void sendOrderConfirmation(Order order) {
    // A real implementation would inject JavaMailSender and render a Thymeleaf template.
    // Any JavaMail-specific exception is caught here and either retried or logged —
    // it must not cross the port boundary as a JavaMail type.
    String recipient = order.getCustomerId() + "@example.com";
    log.info(
        "Sending order confirmation to {} for order {} (total: {})",
        recipient,
        order.getId(),
        order.getTotal().getAmount());
  }
}

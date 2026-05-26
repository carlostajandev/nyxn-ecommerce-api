package com.nyxn.ecommerce.solid.orders.infrastructure.payment;

import com.nyxn.ecommerce.solid.orders.ports.out.PaymentPort;
import java.math.BigDecimal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stripe implementation of {@link PaymentPort}.
 *
 * <p>OCP fix: this class is the <em>only</em> thing that changes when Stripe is replaced by another
 * provider. The application service ({@link
 * com.nyxn.ecommerce.solid.orders.application.PlaceOrderService}) and the domain aggregate are
 * untouched. In the legacy design, adding a provider meant opening {@code processOrder()} and
 * writing another {@code else if} branch — accumulating risk with every change.
 *
 * <p>DIP fix: the application service holds a reference typed as {@link PaymentPort}, not as {@code
 * StripePaymentAdapter}. Spring's DI container injects the right implementation at startup. If we
 * deploy a PayPal adapter and a Stripe adapter simultaneously, the selection logic lives in a
 * factory or qualifier — not in business code.
 *
 * <p>Note: the actual Stripe SDK call is simulated here. A real adapter would inject the Stripe
 * client via constructor and handle Stripe-specific exceptions, translating them into domain
 * exceptions before they cross the port boundary.
 */
@Component
public class StripePaymentAdapter implements PaymentPort {

  private static final Logger log = LoggerFactory.getLogger(StripePaymentAdapter.class);

  @Override
  public String charge(String customerId, BigDecimal amount) {
    // In a real implementation: StripeClient.charges().create(params)
    // Any Stripe-specific exception is caught here and re-thrown as a domain exception
    // so the application service never sees Stripe's SDK types.
    log.info("Charging ${} to customer {} via Stripe", amount, customerId);
    return "stripe_" + UUID.randomUUID();
  }
}

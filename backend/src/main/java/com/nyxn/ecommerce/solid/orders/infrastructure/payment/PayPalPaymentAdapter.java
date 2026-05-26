package com.nyxn.ecommerce.solid.orders.infrastructure.payment;

import com.nyxn.ecommerce.solid.orders.ports.out.PaymentPort;
import java.math.BigDecimal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PayPal implementation of {@link PaymentPort}.
 *
 * <p>This class exists purely to demonstrate the OCP fix: adding PayPal support required writing
 * this new class — not editing any existing class. In the legacy design, adding PayPal meant
 * opening {@code LegacyOrderService.processOrder()} and inserting a new {@code else if} branch,
 * which put every existing payment path at risk of regression.
 *
 * <p>This adapter is NOT registered as a {@code @Component} to avoid conflicting with {@link
 * StripePaymentAdapter} in the shared DI context. In a real project, a qualifier or conditional
 * bean ({@code @ConditionalOnProperty}) would select the right adapter based on configuration.
 */
public class PayPalPaymentAdapter implements PaymentPort {

  private static final Logger log = LoggerFactory.getLogger(PayPalPaymentAdapter.class);

  @Override
  public String charge(String customerId, BigDecimal amount) {
    log.info("Charging ${} to customer {} via PayPal", amount, customerId);
    return "paypal_" + UUID.randomUUID();
  }
}

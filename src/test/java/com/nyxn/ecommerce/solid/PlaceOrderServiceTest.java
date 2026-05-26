package com.nyxn.ecommerce.solid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.nyxn.ecommerce.solid.orders.application.InsufficientOrderStockException;
import com.nyxn.ecommerce.solid.orders.application.PlaceOrderService;
import com.nyxn.ecommerce.solid.orders.domain.Order;
import com.nyxn.ecommerce.solid.orders.domain.OrderStatus;
import com.nyxn.ecommerce.solid.orders.ports.in.PlaceOrderCommand;
import com.nyxn.ecommerce.solid.orders.ports.out.InventoryPort;
import com.nyxn.ecommerce.solid.orders.ports.out.NotificationPort;
import com.nyxn.ecommerce.solid.orders.ports.out.OrderEventPort;
import com.nyxn.ecommerce.solid.orders.ports.out.OrderPersistencePort;
import com.nyxn.ecommerce.solid.orders.ports.out.PaymentPort;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link PlaceOrderService}.
 *
 * <p>These tests demonstrate the most concrete benefit of the SOLID refactor: every port is a plain
 * Java interface, so Mockito can create a stub with a single annotation. No Spring context, no
 * database, no SMTP server, no Pub/Sub emulator — the full order-placement flow is exercised in
 * milliseconds with no I/O.
 *
 * <p>In the legacy design, testing {@code LegacyOrderService} was impossible in isolation: it
 * constructed {@code new StripeGateway()} and {@code new SmtpEmailClient()} internally, forcing
 * tests to either hit real external services or use bytecode manipulation (PowerMock) to intercept
 * the constructors — both approaches are fragile and slow.
 */
@ExtendWith(MockitoExtension.class)
class PlaceOrderServiceTest {

  // Each dependency is a mock of an interface. No concrete class is referenced here,
  // which means the test has zero knowledge of Stripe, SMTP, Pub/Sub, or JPA.
  @Mock private PaymentPort paymentPort;
  @Mock private InventoryPort inventoryPort;
  @Mock private NotificationPort notificationPort;
  @Mock private OrderPersistencePort orderPersistencePort;
  @Mock private OrderEventPort orderEventPort;

  private PlaceOrderService service;

  @BeforeEach
  void setUp() {
    // Constructor injection: the service receives its dependencies here.
    // No Spring context needed — this is why DIP matters for testability.
    service =
        new PlaceOrderService(
            paymentPort, inventoryPort, notificationPort, orderPersistencePort, orderEventPort);
  }

  @Test
  void execute_whenStockAvailable_thenOrderIsConfirmed() {
    // Arrange
    UUID productId = UUID.randomUUID();
    PlaceOrderCommand command = new PlaceOrderCommand("customer-1", productId, 5, "STRIPE");

    given(inventoryPort.isAvailable(any(), anyInt())).willReturn(true);
    given(paymentPort.charge(anyString(), any())).willReturn("stripe_ref_123");
    given(orderPersistencePort.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));

    // Act
    Order result = service.execute(command);

    // Assert
    assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    assertThat(result.getPaymentReference()).isEqualTo("stripe_ref_123");
    assertThat(result.getCustomerId()).isEqualTo("customer-1");

    // Verify the complete workflow was exercised in the correct order:
    // stock checked → payment charged → stock deducted → order saved
    then(inventoryPort).should().isAvailable(any(), anyInt());
    then(paymentPort).should().charge(anyString(), any());
    then(inventoryPort).should().deduct(any(), anyInt());
    then(orderPersistencePort).should().save(any(Order.class));
  }

  @Test
  void execute_whenStockInsufficient_thenNoPaymentAttempted() {
    // Arrange: stock check fails
    UUID productId = UUID.randomUUID();
    PlaceOrderCommand command = new PlaceOrderCommand("customer-2", productId, 200, "STRIPE");

    given(inventoryPort.isAvailable(any(), anyInt())).willReturn(false);

    // Act + Assert
    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(InsufficientOrderStockException.class)
        .hasMessageContaining("does not have enough stock");

    // Critical assertion: payment must never be charged when stock is unavailable.
    // In the legacy design this guard was suppressible by subclassing — LSP violation.
    then(paymentPort).should(never()).charge(anyString(), any());
    then(orderPersistencePort).should(never()).save(any());
  }

  @Test
  void generateReport_returnsProcessedCount() {
    // ISP in action: the test casts to OrderReportUseCase to access the reporting API.
    // A controller that only has a PlaceOrderUseCase reference cannot reach this method.
    assertThat(service.generateReport()).contains("Total orders processed: 0");
  }
}

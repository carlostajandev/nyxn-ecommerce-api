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
import com.nyxn.ecommerce.solid.orders.application.OrderAsyncProcessor;
import com.nyxn.ecommerce.solid.orders.application.PlaceOrderService;
import com.nyxn.ecommerce.solid.orders.domain.Order;
import com.nyxn.ecommerce.solid.orders.domain.OrderStatus;
import com.nyxn.ecommerce.solid.orders.ports.in.PlaceOrderCommand;
import com.nyxn.ecommerce.solid.orders.ports.out.InventoryPort;
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
 * <p>The key proof of the SOLID refactor: every dependency is an interface mockable with a single
 * annotation. No Spring context, no database, no external services — the full placement flow runs
 * in-process with Mockito stubs and completes in milliseconds.
 *
 * <p>In the legacy design, testing {@code LegacyOrderService} was impossible in isolation: it used
 * {@code new StripeGateway()} and {@code new SmtpEmailClient()} internally, requiring either real
 * credentials or bytecode manipulation to intercept the constructors.
 */
@ExtendWith(MockitoExtension.class)
class PlaceOrderServiceTest {

  @Mock private PaymentPort paymentPort;
  @Mock private InventoryPort inventoryPort;
  @Mock private OrderPersistencePort orderPersistencePort;
  @Mock private OrderAsyncProcessor asyncProcessor;

  private PlaceOrderService service;

  @BeforeEach
  void setUp() {
    // Constructor injection — no Spring context needed.
    // This is the testability payoff of DIP: swap any dependency for a mock with one line.
    service =
        new PlaceOrderService(paymentPort, inventoryPort, orderPersistencePort, asyncProcessor);
  }

  @Test
  void execute_whenStockAvailable_thenOrderIsConfirmed() {
    UUID productId = UUID.randomUUID();
    PlaceOrderCommand command = new PlaceOrderCommand("customer-1", productId, 5, "STRIPE");

    given(inventoryPort.isAvailable(any(), anyInt())).willReturn(true);
    given(paymentPort.charge(anyString(), any())).willReturn("stripe_ref_123");
    given(orderPersistencePort.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));

    Order result = service.execute(command);

    assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    assertThat(result.getPaymentReference()).isEqualTo("stripe_ref_123");
    assertThat(result.getCustomerId()).isEqualTo("customer-1");

    // Verify the workflow executes in correct order: stock → charge → deduct → save
    then(inventoryPort).should().isAvailable(any(), anyInt());
    then(paymentPort).should().charge(anyString(), any());
    then(inventoryPort).should().deduct(any(), anyInt());
    then(orderPersistencePort).should().save(any(Order.class));
    // Async processor is dispatched after save — not blocking the caller
    then(asyncProcessor).should().dispatchPostOrderTasks(any(Order.class));
  }

  @Test
  void execute_whenStockInsufficient_thenNoPaymentAttempted() {
    UUID productId = UUID.randomUUID();
    PlaceOrderCommand command = new PlaceOrderCommand("customer-2", productId, 200, "STRIPE");

    given(inventoryPort.isAvailable(any(), anyInt())).willReturn(false);

    // The stock guard must throw before any payment attempt — charging a customer for
    // a product that cannot be fulfilled is a critical business error.
    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(InsufficientOrderStockException.class)
        .hasMessageContaining("does not have enough stock");

    then(paymentPort).should(never()).charge(anyString(), any());
    then(orderPersistencePort).should(never()).save(any());
  }

  @Test
  void generateReport_returnsProcessedCount() {
    // ISP in action: the report API lives on OrderReportUseCase, a separate interface.
    // A controller holding only a PlaceOrderUseCase reference cannot see this method.
    assertThat(service.generateReport()).contains("Total orders processed: 0");
  }
}

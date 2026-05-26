package com.nyxn.ecommerce.solid.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyxn.ecommerce.application.dto.CreateProductCommand;
import com.nyxn.ecommerce.solid.orders.ports.in.PlaceOrderCommand;
import com.nyxn.ecommerce.solid.orders.ports.out.InventoryPort;
import com.nyxn.ecommerce.solid.orders.ports.out.NotificationPort;
import com.nyxn.ecommerce.solid.orders.ports.out.OrderEventPort;
import com.nyxn.ecommerce.solid.orders.ports.out.PaymentPort;
import com.nyxn.ecommerce.testconfig.BaseIntegrationTest;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration test for the Orders API.
 *
 * <p>Verifies the full HTTP → application service → JPA → HTTP round trip against a real PostgreSQL
 * container started by {@link BaseIntegrationTest}. External adapters (payment, notification, event
 * publishing) are replaced with {@code @MockBean} stubs — we are testing the ordering workflow and
 * persistence, not Stripe or SMTP.
 *
 * <p>{@link InventoryPort} is NOT mocked: the test creates a real product first, so the inventory
 * check exercises the actual {@link
 * com.nyxn.ecommerce.solid.orders.infrastructure.inventory.ProductRepositoryInventoryAdapter}
 * reading from PostgreSQL. This validates the full integration path from order placement through
 * inventory deduction to order persistence.
 */
class OrderControllerIT extends BaseIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  // External services replaced with stubs so the test has no network dependencies.
  // The @MockBean annotation replaces the real Spring bean in the application context.
  @MockBean private PaymentPort paymentPort;
  @MockBean private NotificationPort notificationPort;
  @MockBean private OrderEventPort orderEventPort;

  @Test
  void placeOrder_whenProductExistsAndStockAvailable_thenReturn201WithLocation() throws Exception {
    UUID productId = createProductAndGetId();
    given(paymentPort.charge(anyString(), any()))
        .willReturn("stripe_test_ref_" + UUID.randomUUID());

    PlaceOrderCommand command = new PlaceOrderCommand("customer-it-01", productId, 3, "STRIPE");

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(command)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.customerId").value("customer-it-01"))
            .andExpect(jsonPath("$.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.paymentReference").isNotEmpty())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andReturn();

    String location = result.getResponse().getHeader("Location");
    assertThat(location).isNotNull().contains("/api/v1/orders/");
  }

  @Test
  void getOrder_whenOrderExists_thenReturn200() throws Exception {
    UUID productId = createProductAndGetId();
    given(paymentPort.charge(anyString(), any())).willReturn("stripe_ref_get_test");

    // Place an order first
    PlaceOrderCommand command = new PlaceOrderCommand("customer-it-02", productId, 1, "STRIPE");
    MvcResult placed =
        mockMvc
            .perform(
                post("/api/v1/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(command)))
            .andExpect(status().isCreated())
            .andReturn();

    String orderId =
        objectMapper.readTree(placed.getResponse().getContentAsString()).get("id").asText();

    // Retrieve it by ID
    mockMvc
        .perform(get("/api/v1/orders/" + orderId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(orderId))
        .andExpect(jsonPath("$.status").value("CONFIRMED"));
  }

  @Test
  void getOrder_whenOrderDoesNotExist_thenReturn404() throws Exception {
    mockMvc
        .perform(get("/api/v1/orders/00000000-0000-0000-0000-000000000099"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  void placeOrder_whenQuantityIsZero_thenReturn400() throws Exception {
    PlaceOrderCommand command =
        new PlaceOrderCommand("customer-it-bad", UUID.randomUUID(), 0, "STRIPE");

    mockMvc
        .perform(
            post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(command)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  void placeOrder_whenStockInsufficient_thenReturn422() throws Exception {
    UUID productId = createProductAndGetId(); // product has 200 units
    given(paymentPort.charge(anyString(), any())).willReturn("stripe_ref_overflow");

    // Request more than available stock
    PlaceOrderCommand command =
        new PlaceOrderCommand("customer-it-overflow", productId, 999, "STRIPE");

    mockMvc
        .perform(
            post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(command)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.status").value(422));
  }

  // ─── Helper ────────────────────────────────────────────────────────────────

  /**
   * Creates a product via the Products API and returns its ID.
   *
   * <p>The Orders IT test depends on a real product so that the inventory adapter can check and
   * deduct stock against PostgreSQL. Creating the product via HTTP (rather than directly through
   * the repository) ensures the full Products creation path is exercised as well.
   */
  private UUID createProductAndGetId() throws Exception {
    CreateProductCommand cmd =
        new CreateProductCommand(
            "IT Test Product " + UUID.randomUUID(),
            "Product for integration testing",
            new BigDecimal("49.99"),
            200,
            "TEST");

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(cmd)))
            .andExpect(status().isCreated())
            .andReturn();

    String idStr =
        objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    return UUID.fromString(idStr);
  }
}

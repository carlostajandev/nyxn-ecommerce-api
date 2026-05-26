package com.nyxn.ecommerce.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyxn.ecommerce.application.dto.CreateProductCommand;
import com.nyxn.ecommerce.application.dto.UpdateProductCommand;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test: corre con PostgreSQL y Redis reales via Testcontainers. Profile "test" usa
 * application-test.yml con contenedores. Nombre de test: method_whenCondition_thenBehavior.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class ProductControllerIT {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void createProduct_whenValidRequest_thenReturn201WithLocation() throws Exception {
    CreateProductCommand command =
        new CreateProductCommand(
            "Mechanical Keyboard",
            "RGB mechanical keyboard",
            new BigDecimal("149.99"),
            200,
            "PERIPHERALS");

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(command)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Mechanical Keyboard"))
            .andExpect(jsonPath("$.price").value(149.99))
            .andExpect(jsonPath("$.stock").value(200))
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andReturn();

    String locationHeader = result.getResponse().getHeader("Location");
    assertThat(locationHeader).isNotNull().contains("/api/v1/products/");
  }

  @Test
  void createProduct_whenNameIsBlank_thenReturn400() throws Exception {
    CreateProductCommand command =
        new CreateProductCommand("", "desc", new BigDecimal("10.00"), 10, "MISC");

    mockMvc
        .perform(
            post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(command)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.message").isNotEmpty())
        .andExpect(jsonPath("$.timestamp").isNotEmpty())
        .andExpect(jsonPath("$.path").value("/api/v1/products"));
  }

  @Test
  void getProduct_whenProductDoesNotExist_thenReturn404() throws Exception {
    mockMvc
        .perform(get("/api/v1/products/00000000-0000-0000-0000-000000000000"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  void fullCrudLifecycle_whenAllOperations_thenSucceed() throws Exception {
    // Create
    CreateProductCommand createCmd =
        new CreateProductCommand(
            "Wireless Mouse",
            "Ergonomic wireless mouse",
            new BigDecimal("59.99"),
            100,
            "PERIPHERALS");

    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/v1/products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createCmd)))
            .andExpect(status().isCreated())
            .andReturn();

    String responseBody = createResult.getResponse().getContentAsString();
    String productId = objectMapper.readTree(responseBody).get("id").asText();

    // Get
    mockMvc
        .perform(get("/api/v1/products/" + productId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(productId));

    // Update
    UpdateProductCommand updateCmd =
        new UpdateProductCommand(
            "Wireless Mouse Pro",
            "Updated description",
            new BigDecimal("79.99"),
            80,
            "PERIPHERALS");

    mockMvc
        .perform(
            put("/api/v1/products/" + productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateCmd)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Wireless Mouse Pro"))
        .andExpect(jsonPath("$.price").value(79.99));

    // Delete
    mockMvc.perform(delete("/api/v1/products/" + productId)).andExpect(status().isNoContent());

    // Verify gone
    mockMvc.perform(get("/api/v1/products/" + productId)).andExpect(status().isNotFound());
  }
}

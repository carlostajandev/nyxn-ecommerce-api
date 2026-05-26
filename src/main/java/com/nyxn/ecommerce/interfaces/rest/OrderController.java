package com.nyxn.ecommerce.interfaces.rest;

import com.nyxn.ecommerce.solid.orders.application.dto.OrderResponse;
import com.nyxn.ecommerce.solid.orders.application.mappers.OrderDtoMapper;
import com.nyxn.ecommerce.solid.orders.domain.OrderId;
import com.nyxn.ecommerce.solid.orders.ports.in.GetOrderUseCase;
import com.nyxn.ecommerce.solid.orders.ports.in.PlaceOrderCommand;
import com.nyxn.ecommerce.solid.orders.ports.in.PlaceOrderUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * REST controller for the Orders API.
 *
 * <p>Single responsibility: translate HTTP into use-case commands and return the response. No
 * business logic here. Depends on port interfaces only — the concrete service implementations are
 * invisible to this class and can be swapped without touching it.
 *
 * <p>Following the same conventions as {@link ProductController}: constructor injection, no field
 * injection, explicit status codes, Location header on 201.
 */
@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders", description = "Order management endpoints")
public class OrderController {

  private final PlaceOrderUseCase placeOrderUseCase;
  private final GetOrderUseCase getOrderUseCase;
  private final OrderDtoMapper mapper;

  public OrderController(
      PlaceOrderUseCase placeOrderUseCase, GetOrderUseCase getOrderUseCase, OrderDtoMapper mapper) {
    this.placeOrderUseCase = placeOrderUseCase;
    this.getOrderUseCase = getOrderUseCase;
    this.mapper = mapper;
  }

  @Operation(
      summary = "Place an order",
      description = "Validates stock, charges payment, and confirms the order")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Order placed"),
    @ApiResponse(responseCode = "400", description = "Invalid request body"),
    @ApiResponse(responseCode = "422", description = "Insufficient stock")
  })
  @PostMapping
  public ResponseEntity<OrderResponse> place(@Valid @RequestBody PlaceOrderCommand command) {
    var order = placeOrderUseCase.execute(command);
    var response = mapper.toResponse(order);
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(response.id())
            .toUri();
    return ResponseEntity.created(location).body(response);
  }

  @Operation(summary = "Get order by ID")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Order found"),
    @ApiResponse(responseCode = "404", description = "Order not found")
  })
  @GetMapping("/{id}")
  public ResponseEntity<OrderResponse> getById(@PathVariable UUID id) {
    var order = getOrderUseCase.findById(OrderId.of(id));
    return ResponseEntity.ok(mapper.toResponse(order));
  }

  @Operation(summary = "List orders with pagination")
  @ApiResponse(responseCode = "200", description = "Paginated list of orders")
  @GetMapping
  public ResponseEntity<Page<OrderResponse>> list(
      @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
    return ResponseEntity.ok(mapper.toResponsePage(getOrderUseCase.findAll(pageable)));
  }
}

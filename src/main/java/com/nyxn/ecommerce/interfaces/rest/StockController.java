package com.nyxn.ecommerce.interfaces.rest;

import com.nyxn.ecommerce.application.dto.StockReservationRequest;
import com.nyxn.ecommerce.domain.ports.in.ReserveStockUseCase;
import com.nyxn.ecommerce.domain.valueobject.ProductId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for high-throughput stock operations.
 *
 * <p>These endpoints are the entry point for the Cyber-Day scenario: hundreds of concurrent clients
 * call {@code POST /reserve} for the same product. The controller itself is stateless and
 * thread-safe — all serialisation happens downstream in Redis (atomic Lua script) and PostgreSQL
 * (optimistic locking with retry).
 *
 * <p>Depends only on {@link ReserveStockUseCase} — a narrow inbound port. The concrete service
 * implementation and its retry / cache behaviour are invisible to this class.
 */
@RestController
@RequestMapping("/api/v1/products/{id}/stock")
@Tag(name = "Stock", description = "High-throughput stock reservation endpoints")
public class StockController {

  private final ReserveStockUseCase reserveStockUseCase;

  public StockController(ReserveStockUseCase reserveStockUseCase) {
    this.reserveStockUseCase = reserveStockUseCase;
  }

  @Operation(
      summary = "Reserve stock",
      description =
          "Atomically reserves units via Redis Lua script, then commits to PostgreSQL with "
              + "optimistic-lock retry. Returns 204 on success.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Stock reserved"),
    @ApiResponse(responseCode = "400", description = "Invalid quantity"),
    @ApiResponse(responseCode = "409", description = "Optimistic lock conflict after retries"),
    @ApiResponse(responseCode = "422", description = "Insufficient stock")
  })
  @PostMapping("/reserve")
  public ResponseEntity<Void> reserve(
      @PathVariable UUID id, @Valid @RequestBody StockReservationRequest request) {
    reserveStockUseCase.reserve(ProductId.of(id), request.quantity());
    return ResponseEntity.noContent().build();
  }

  @Operation(
      summary = "Release stock",
      description = "Returns reserved units to available stock. Called on order cancellation.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Stock released"),
    @ApiResponse(responseCode = "400", description = "Invalid quantity")
  })
  @PostMapping("/release")
  public ResponseEntity<Void> release(
      @PathVariable UUID id, @Valid @RequestBody StockReservationRequest request) {
    reserveStockUseCase.release(ProductId.of(id), request.quantity());
    return ResponseEntity.noContent().build();
  }
}

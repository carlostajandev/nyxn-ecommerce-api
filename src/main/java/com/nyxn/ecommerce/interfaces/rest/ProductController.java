package com.nyxn.ecommerce.interfaces.rest;

import com.nyxn.ecommerce.application.dto.CreateProductCommand;
import com.nyxn.ecommerce.application.dto.ProductResponse;
import com.nyxn.ecommerce.application.dto.UpdateProductCommand;
import com.nyxn.ecommerce.application.mappers.ProductDtoMapper;
import com.nyxn.ecommerce.domain.ports.in.CreateProductUseCase;
import com.nyxn.ecommerce.domain.ports.in.DeleteProductUseCase;
import com.nyxn.ecommerce.domain.ports.in.GetProductUseCase;
import com.nyxn.ecommerce.domain.ports.in.UpdateProductUseCase;
import com.nyxn.ecommerce.domain.valueobject.ProductId;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Controlador de productos. Responsabilidad unica: recibir HTTP, delegar al puerto de entrada,
 * devolver HTTP. Cero logica de negocio aqui. El controlador no conoce los use cases concretos —
 * solo las interfaces de puerto.
 */
@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products", description = "Product management endpoints")
public class ProductController {

  private final CreateProductUseCase createProductUseCase;
  private final GetProductUseCase getProductUseCase;
  private final UpdateProductUseCase updateProductUseCase;
  private final DeleteProductUseCase deleteProductUseCase;
  private final ProductDtoMapper mapper;

  public ProductController(
      CreateProductUseCase createProductUseCase,
      GetProductUseCase getProductUseCase,
      UpdateProductUseCase updateProductUseCase,
      DeleteProductUseCase deleteProductUseCase,
      ProductDtoMapper mapper) {
    this.createProductUseCase = createProductUseCase;
    this.getProductUseCase = getProductUseCase;
    this.updateProductUseCase = updateProductUseCase;
    this.deleteProductUseCase = deleteProductUseCase;
    this.mapper = mapper;
  }

  @Operation(summary = "Create a product", description = "Creates a new product in the catalog")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Product created"),
    @ApiResponse(responseCode = "400", description = "Invalid request body"),
    @ApiResponse(responseCode = "422", description = "Business rule violation")
  })
  @PostMapping
  public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductCommand command) {
    var product = createProductUseCase.execute(command);
    var response = mapper.toResponse(product);
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(response.id())
            .toUri();
    return ResponseEntity.created(location).body(response);
  }

  @Operation(summary = "Get product by ID")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Product found"),
    @ApiResponse(responseCode = "404", description = "Product not found")
  })
  @GetMapping("/{id}")
  public ResponseEntity<ProductResponse> getById(@PathVariable UUID id) {
    var product = getProductUseCase.findById(ProductId.of(id));
    return ResponseEntity.ok(mapper.toResponse(product));
  }

  @Operation(summary = "List products with pagination")
  @ApiResponse(responseCode = "200", description = "Paginated list of products")
  @GetMapping
  public ResponseEntity<Page<ProductResponse>> list(
      @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
    Page<ProductResponse> page = mapper.toResponsePage(getProductUseCase.findAll(pageable));
    return ResponseEntity.ok(page);
  }

  @Operation(summary = "Update a product")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Product updated"),
    @ApiResponse(responseCode = "400", description = "Invalid request body"),
    @ApiResponse(responseCode = "404", description = "Product not found"),
    @ApiResponse(responseCode = "409", description = "Stock conflict (optimistic locking)")
  })
  @PutMapping("/{id}")
  public ResponseEntity<ProductResponse> update(
      @PathVariable UUID id, @Valid @RequestBody UpdateProductCommand command) {
    var updated = updateProductUseCase.execute(ProductId.of(id), command);
    return ResponseEntity.ok(mapper.toResponse(updated));
  }

  @Operation(summary = "Delete a product")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Product deleted"),
    @ApiResponse(responseCode = "404", description = "Product not found")
  })
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    deleteProductUseCase.execute(ProductId.of(id));
    return ResponseEntity.noContent().build();
  }
}

package com.nyxn.ecommerce.interfaces.handlers;

import com.nyxn.ecommerce.domain.exceptions.InsufficientStockException;
import com.nyxn.ecommerce.domain.exceptions.InvalidMoneyException;
import com.nyxn.ecommerce.domain.exceptions.ProductNotFoundException;
import com.nyxn.ecommerce.domain.exceptions.StockConflictException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centralized exception handler.
 *
 * <p>Maps specific domain exceptions to semantic HTTP status codes. Never catches the generic
 * {@code Exception}: doing so would mask unexpected failures behind a 4xx and make production
 * incidents much harder to diagnose.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ProductNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleProductNotFound(
      ProductNotFoundException ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ErrorResponse.of(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI()));
  }

  @ExceptionHandler(InsufficientStockException.class)
  public ResponseEntity<ErrorResponse> handleInsufficientStock(
      InsufficientStockException ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
        .body(
            ErrorResponse.of(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request.getRequestURI()));
  }

  @ExceptionHandler(InvalidMoneyException.class)
  public ResponseEntity<ErrorResponse> handleInvalidMoney(
      InvalidMoneyException ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI()));
  }

  @ExceptionHandler(StockConflictException.class)
  public ResponseEntity<ErrorResponse> handleStockConflict(
      StockConflictException ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ErrorResponse.of(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining("; "));
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, message, request.getRequestURI()));
  }

  public record ErrorResponse(Instant timestamp, int status, String message, String path) {

    static ErrorResponse of(HttpStatus status, String message, String path) {
      return new ErrorResponse(Instant.now(), status.value(), message, path);
    }
  }
}

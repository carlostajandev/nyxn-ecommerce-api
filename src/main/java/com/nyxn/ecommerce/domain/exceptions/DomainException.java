package com.nyxn.ecommerce.domain.exceptions;

/**
 * Base para todas las excepciones de dominio. Runtime — no fuerza checked exceptions en el dominio.
 */
public abstract class DomainException extends RuntimeException {

  protected DomainException(String message) {
    super(message);
  }

  protected DomainException(String message, Throwable cause) {
    super(message, cause);
  }
}

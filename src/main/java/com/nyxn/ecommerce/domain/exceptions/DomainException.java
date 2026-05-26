package com.nyxn.ecommerce.domain.exceptions;

/**
 * Base class for all domain exceptions.
 *
 * <p>Extends {@code RuntimeException} to avoid checked exceptions leaking domain details through
 * port signatures. Forcing {@code throws} declarations on use-case interfaces would couple callers
 * to internal implementation choices.
 */
public abstract class DomainException extends RuntimeException {

  protected DomainException(String message) {
    super(message);
  }

  protected DomainException(String message, Throwable cause) {
    super(message, cause);
  }
}

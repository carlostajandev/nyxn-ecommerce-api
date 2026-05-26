package com.nyxn.ecommerce.infrastructure.stock;

import com.nyxn.ecommerce.domain.exceptions.InsufficientStockException;
import com.nyxn.ecommerce.domain.exceptions.StockConflictException;
import com.nyxn.ecommerce.domain.ports.out.ProductRepository;
import com.nyxn.ecommerce.domain.valueobject.ProductId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Executes a stock deduction against PostgreSQL with optimistic-lock retry.
 *
 * <p>This class is deliberately extracted from {@link
 * com.nyxn.ecommerce.application.usecases.StockReservationService} for two compounding reasons:
 *
 * <ol>
 *   <li><b>Spring AOP proxy composition</b>: {@code @Retryable} and {@code @Transactional} are both
 *       proxy-based. For retry to wrap each individual transaction attempt (rather than the entire
 *       retry loop sharing one transaction), the two annotations must be on the same method in a
 *       Spring-managed bean, called from <em>outside</em> that bean. The retry interceptor (outer
 *       proxy) catches {@code OptimisticLockException}, rolls back the transaction (inner proxy),
 *       and then calls the method again — each attempt gets a fresh transaction and a fresh
 *       Hibernate session, so the entity is reloaded from DB on every try. If both annotations were
 *       on the same bean as the caller, the caller's internal {@code this} reference bypasses the
 *       proxies and the retry never fires.
 *   <li><b>SRP</b>: the retry/recovery policy for DB writes is a separate concern from the
 *       cache-coordination logic in the reservation service.
 * </ol>
 *
 * <h2>Retry configuration</h2>
 *
 * <ul>
 *   <li>{@code maxAttempts = 3}: after three failures the {@link #recover} method runs.
 *   <li>{@code delay = 50ms, multiplier = 2.0}: 50 ms → 100 ms → 200 ms between attempts.
 *   <li>{@code random = true}: adds up to 50 % random jitter to the delay — prevents all retrying
 *       clients from hammering the DB in lockstep (coordinated omission / thundering herd).
 * </ul>
 */
@Component
public class ProductStockUpdater {

  private static final Logger log = LoggerFactory.getLogger(ProductStockUpdater.class);

  private final ProductRepository productRepository;

  public ProductStockUpdater(ProductRepository productRepository) {
    this.productRepository = productRepository;
  }

  /**
   * Deducts {@code quantity} units from the product in a single transaction.
   *
   * <p>Hibernate generates: {@code UPDATE products SET stock=?, version=? WHERE id=? AND version=?}
   * If another thread updated the row between our SELECT and this UPDATE, the WHERE clause matches
   * zero rows — Hibernate detects this and throws {@code ObjectOptimisticLockingFailureException}.
   * Spring Retry intercepts that exception, waits with exponential backoff + jitter, and retries
   * the entire method — starting a fresh transaction that reloads the latest version from the DB.
   *
   * @param productId product whose stock is reduced
   * @param quantity units to deduct
   * @throws InsufficientStockException if the reloaded entity has insufficient stock
   * @throws ObjectOptimisticLockingFailureException propagated on version conflict (triggers retry)
   */
  @Retryable(
      retryFor = ObjectOptimisticLockingFailureException.class,
      maxAttempts = 3,
      backoff = @Backoff(delay = 50, multiplier = 2.0, random = true))
  @Transactional
  public void decreaseStock(ProductId productId, int quantity) {
    // Re-read inside the transaction on every retry — we need the latest version number.
    // Using a stale entity from before the retry would always reproduce the conflict.
    var product =
        productRepository
            .findById(productId)
            .orElseThrow(
                () ->
                    new InsufficientStockException(
                        "Product not found during stock deduction: " + productId));

    // Domain method enforces the stock >= 0 invariant and throws InsufficientStockException.
    // That exception is NOT in the retryFor list — insufficient stock is a business error,
    // not a transient conflict, and retrying it would not change the outcome.
    product.decreaseStock(quantity);
    productRepository.save(product);

    log.debug(
        "Stock decreased for {} by {}; remaining: {}",
        productId,
        quantity,
        product.getStock().getQuantity());
  }

  /**
   * Called by Spring Retry when all {@link #decreaseStock} attempts are exhausted.
   *
   * <p>At this point the optimistic-lock conflict persisted across three attempts — something is
   * very wrong (runaway concurrent writes, long GC pauses, etc.). The caller must compensate any
   * prior cache reservation. We translate the technical lock exception into a domain exception so
   * that the HTTP layer can return HTTP 409 Conflict.
   *
   * @param ex the last {@code ObjectOptimisticLockingFailureException} thrown
   * @param productId the product that could not be updated
   * @param quantity the quantity that could not be reserved
   */
  @Recover
  public void recover(
      ObjectOptimisticLockingFailureException ex, ProductId productId, int quantity) {
    log.error(
        "All retry attempts exhausted for stock deduction on product {} qty {}",
        productId,
        quantity,
        ex);
    throw new StockConflictException(
        "Stock update conflict persisted after retries for product: " + productId);
  }

  /**
   * Adds {@code quantity} units back to the product's stock in a single transaction.
   *
   * <p>Used as a compensating operation when a downstream failure (e.g. order persistence) means a
   * reservation must be undone. Not retried — if the release fails, the stock is under-counted in
   * the DB until a reconciliation job corrects it (acceptable trade-off for simplicity).
   *
   * @param productId product whose stock is released
   * @param quantity units to return
   */
  @Transactional
  public void increaseStock(ProductId productId, int quantity) {
    productRepository
        .findById(productId)
        .ifPresent(
            product -> {
              product.increaseStock(quantity);
              productRepository.save(product);
              log.debug("Stock released for {} by {}", productId, quantity);
            });
  }
}

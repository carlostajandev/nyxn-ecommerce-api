package com.nyxn.ecommerce.solid.legacy;

/**
 * ⚠️ INTENTIONALLY BAD INTERFACE — demonstrates an ISP violation.
 *
 * <p>This interface bundles two unrelated capabilities — order processing and report generation —
 * into a single contract. Any class that needs one capability is forced to implement the other.
 *
 * <p>The concrete consequence: every unit test for order processing must stub or implement {@link
 * #generateReport()}, which has nothing to do with placing an order. Mock frameworks silently
 * return {@code null}, hiding the violation until a reviewer notices the dead stub.
 *
 * <p>See {@link com.nyxn.ecommerce.solid.orders.ports.in.PlaceOrderUseCase} and the reporting port
 * for the corrected, segregated design.
 */
public interface OrderOperations {

  /**
   * Processes a customer order end-to-end.
   *
   * @return generated order identifier
   */
  String processOrder(String customerId, String productId, int quantity, String paymentMethod);

  /**
   * Generates an operational report.
   *
   * <p>ISP violation: this method belongs to a reporting concern that is completely orthogonal to
   * order processing. A payment processor has no reason to know about reporting formats.
   *
   * @return a human-readable report string
   */
  String generateReport();
}

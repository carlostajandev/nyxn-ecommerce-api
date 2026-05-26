package com.nyxn.ecommerce.solid.orders.ports.in;

/**
 * Inbound port: order reporting.
 *
 * <p>ISP fix: reporting is a completely separate capability from placing orders. Separating them
 * into two interfaces means:
 *
 * <ul>
 *   <li>A REST controller that only places orders depends on {@link PlaceOrderUseCase} — it never
 *       sees the reporting API and cannot accidentally call it.
 *   <li>A scheduled job that only generates reports depends on this interface — it cannot
 *       accidentally call {@link PlaceOrderUseCase#execute}.
 *   <li>Tests for each concern mock exactly the interface they need, keeping test classes small and
 *       readable.
 * </ul>
 *
 * <p>Compare to the legacy {@link com.nyxn.ecommerce.solid.legacy.OrderOperations} that forced
 * every implementor to carry both responsibilities.
 */
public interface OrderReportUseCase {

  /**
   * Generates an operational summary of all orders processed by the system.
   *
   * @return human-readable report string (a production system would return a typed DTO)
   */
  String generateReport();
}

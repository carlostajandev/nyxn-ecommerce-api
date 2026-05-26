package com.nyxn.ecommerce.solid.orders.application;

import com.nyxn.ecommerce.solid.orders.domain.Order;
import com.nyxn.ecommerce.solid.orders.domain.OrderId;
import com.nyxn.ecommerce.solid.orders.domain.OrderNotFoundException;
import com.nyxn.ecommerce.solid.orders.ports.in.GetOrderUseCase;
import com.nyxn.ecommerce.solid.orders.ports.out.OrderPersistencePort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-side application service for orders.
 *
 * <p>Deliberately separated from {@link PlaceOrderService} — the read side has different
 * transactional requirements (read-only) and different reasons to change (query shapes, sorting)
 * than the write side. Keeping them in the same class would violate SRP as the system grows.
 *
 * <p>All methods are {@code readOnly = true}, which signals to Hibernate that no dirty-checking
 * flush is needed at the end of the transaction. On a read replica this annotation also enables
 * routing to the replica connection pool.
 */
@Service
@Transactional(readOnly = true)
public class GetOrderService implements GetOrderUseCase {

  private final OrderPersistencePort orderPersistencePort;

  public GetOrderService(OrderPersistencePort orderPersistencePort) {
    this.orderPersistencePort = orderPersistencePort;
  }

  @Override
  public Order findById(OrderId id) {
    return orderPersistencePort.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
  }

  @Override
  public Page<Order> findAll(Pageable pageable) {
    return orderPersistencePort.findAll(pageable);
  }
}

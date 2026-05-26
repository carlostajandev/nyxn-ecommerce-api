package com.nyxn.ecommerce.solid.orders.infrastructure.persistence;

import com.nyxn.ecommerce.solid.orders.domain.Order;
import com.nyxn.ecommerce.solid.orders.domain.OrderId;
import com.nyxn.ecommerce.solid.orders.ports.out.OrderPersistencePort;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory implementation of {@link OrderPersistencePort}.
 *
 * <p>This adapter is used in unit tests and local development when a real database is not
 * available. It demonstrates how the DIP makes infrastructure swappable without touching the
 * application layer: a test that injects this adapter and a production deployment that injects a
 * JPA adapter run through exactly the same {@link
 * com.nyxn.ecommerce.solid.orders.application.PlaceOrderService} code.
 *
 * <p>For a production JPA adapter, replace this class with a JPA entity + Spring Data repository,
 * following the same pattern used in {@link
 * com.nyxn.ecommerce.infrastructure.persistence.repository.ProductRepositoryAdapter}.
 */
@Component
public class InMemoryOrderRepository implements OrderPersistencePort {

  // ConcurrentHashMap instead of HashMap: the @Async notification method may read the store
  // concurrently with writes from the transaction thread. A plain HashMap would cause a data race.
  private final Map<OrderId, Order> store = new ConcurrentHashMap<>();

  @Override
  public Order save(Order order) {
    store.put(order.getId(), order);
    return order;
  }

  @Override
  public Optional<Order> findById(OrderId id) {
    return Optional.ofNullable(store.get(id));
  }
}

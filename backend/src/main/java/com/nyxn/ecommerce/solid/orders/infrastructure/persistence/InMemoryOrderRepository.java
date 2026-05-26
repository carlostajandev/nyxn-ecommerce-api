package com.nyxn.ecommerce.solid.orders.infrastructure.persistence;

import com.nyxn.ecommerce.solid.orders.domain.Order;
import com.nyxn.ecommerce.solid.orders.domain.OrderId;
import com.nyxn.ecommerce.solid.orders.ports.out.OrderPersistencePort;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/**
 * In-memory implementation of {@link OrderPersistencePort}.
 *
 * <p>Not registered as a Spring bean — this class is for direct instantiation in unit tests that do
 * not start a Spring context. The production bean is {@link JpaOrderRepositoryAdapter}, which is
 * wired via {@code @Component}.
 *
 * <p>Demonstrating the DIP benefit: {@link
 * com.nyxn.ecommerce.solid.orders.application.PlaceOrderService} runs through the same code paths
 * whether the injected port is this in-memory store or the full JPA adapter. Swapping adapters
 * requires zero changes to the application service.
 */
public class InMemoryOrderRepository implements OrderPersistencePort {

  // ConcurrentHashMap: @Async post-order tasks may read the store concurrently with the save
  // call from the transaction thread. A plain HashMap would be a silent data race.
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

  @Override
  public Page<Order> findAll(Pageable pageable) {
    List<Order> all = new ArrayList<>(store.values());
    int start = (int) pageable.getOffset();
    int end = Math.min(start + pageable.getPageSize(), all.size());
    List<Order> slice = start > all.size() ? List.of() : all.subList(start, end);
    return new PageImpl<>(slice, pageable, all.size());
  }
}

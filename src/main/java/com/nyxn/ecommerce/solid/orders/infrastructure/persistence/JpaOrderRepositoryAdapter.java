package com.nyxn.ecommerce.solid.orders.infrastructure.persistence;

import com.nyxn.ecommerce.solid.orders.domain.Order;
import com.nyxn.ecommerce.solid.orders.domain.OrderId;
import com.nyxn.ecommerce.solid.orders.ports.out.OrderPersistencePort;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * JPA implementation of {@link OrderPersistencePort}.
 *
 * <p>This adapter is the only class that speaks Spring Data JPA on behalf of the Orders domain. The
 * application layer calls {@link OrderPersistencePort} methods using domain types ({@link Order},
 * {@link OrderId}). This class translates to/from JPA entities, keeping the domain clean.
 *
 * <p>Note: {@code @Component} rather than {@code @Repository} — Spring Data's exception translation
 * is already applied by the JPA repository proxy. Adding {@code @Repository} on this adapter would
 * attempt a second translation pass and add confusion without benefit.
 *
 * <p>This is the production adapter. The {@link InMemoryOrderRepository} is kept as a plain class
 * for manual injection in unit tests that do not start a Spring context.
 */
@Component
public class JpaOrderRepositoryAdapter implements OrderPersistencePort {

  private final SpringDataOrderRepository jpaRepo;
  private final OrderEntityMapper mapper;

  public JpaOrderRepositoryAdapter(SpringDataOrderRepository jpaRepo, OrderEntityMapper mapper) {
    this.jpaRepo = jpaRepo;
    this.mapper = mapper;
  }

  @Override
  public Order save(Order order) {
    OrderEntity saved = jpaRepo.save(mapper.toEntity(order));
    return mapper.toDomain(saved);
  }

  @Override
  public Optional<Order> findById(OrderId id) {
    return jpaRepo.findById(id.value()).map(mapper::toDomain);
  }

  @Override
  public Page<Order> findAll(Pageable pageable) {
    return jpaRepo.findAll(pageable).map(mapper::toDomain);
  }
}

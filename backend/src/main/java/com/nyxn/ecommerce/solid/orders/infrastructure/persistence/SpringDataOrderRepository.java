package com.nyxn.ecommerce.solid.orders.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link OrderEntity}.
 *
 * <p>This interface stays in the infrastructure layer — the domain never sees it. The only class
 * that depends on it is {@link JpaOrderRepositoryAdapter}, which translates Spring Data's {@code
 * Page<OrderEntity>} to the domain's {@code Page<Order>} before crossing the port boundary.
 *
 * <p>Spring Data generates the implementation at startup via a JDK dynamic proxy. Custom queries
 * (e.g. find by customer, filter by status) can be added here as {@code @Query} methods without
 * touching any other class.
 */
public interface SpringDataOrderRepository extends JpaRepository<OrderEntity, UUID> {}

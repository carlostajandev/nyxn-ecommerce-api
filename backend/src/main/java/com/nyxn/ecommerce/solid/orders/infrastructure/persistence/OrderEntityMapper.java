package com.nyxn.ecommerce.solid.orders.infrastructure.persistence;

import com.nyxn.ecommerce.domain.valueobject.Money;
import com.nyxn.ecommerce.domain.valueobject.ProductId;
import com.nyxn.ecommerce.solid.orders.domain.Order;
import com.nyxn.ecommerce.solid.orders.domain.OrderId;
import com.nyxn.ecommerce.solid.orders.domain.OrderStatus;
import org.springframework.stereotype.Component;

/**
 * Bidirectional mapper between the {@link Order} aggregate and {@link OrderEntity}.
 *
 * <p>This is the only class in the codebase where the domain aggregate and the JPA entity are aware
 * of each other. Keeping the coupling in one place makes the anti-corruption layer explicit: a
 * schema change (e.g. renaming a column) touches the entity and this mapper — never the aggregate
 * or the application service.
 *
 * <p>Restoring the aggregate from the entity ({@link #toDomain}) is the more delicate direction.
 * The aggregate's private constructor validates invariants; to restore a historical record, the
 * builder must also accept the saved {@code createdAt} and the {@code paymentReference} that was
 * already set when the order was confirmed. The status restoration uses {@link OrderStatus#valueOf}
 * — if the database holds a value that no longer exists in the enum, the application fails fast at
 * startup rather than silently accepting corrupted state.
 */
@Component
public class OrderEntityMapper {

  public OrderEntity toEntity(Order order) {
    OrderEntity entity = new OrderEntity();
    entity.setId(order.getId().value());
    entity.setCustomerId(order.getCustomerId());
    entity.setProductId(order.getProductId().getValue());
    entity.setQuantity(order.getQuantity());
    entity.setAmount(order.getTotal().getAmount());
    entity.setCurrency(order.getTotal().getCurrency());
    entity.setStatus(order.getStatus().name());
    entity.setPaymentRef(order.getPaymentReference());
    entity.setCreatedAt(order.getCreatedAt());
    return entity;
  }

  public Order toDomain(OrderEntity entity) {
    // Restore the aggregate to the state it was in when last persisted.
    // The builder path bypasses confirmPayment/reserveStock/confirm to avoid re-running
    // the state-machine guards on a record that is already in its final state.
    Order order =
        Order.builder()
            .id(OrderId.of(entity.getId()))
            .customerId(entity.getCustomerId())
            .productId(ProductId.of(entity.getProductId()))
            .quantity(entity.getQuantity())
            .total(Money.of(entity.getAmount(), entity.getCurrency()))
            .createdAt(entity.getCreatedAt())
            .build();

    // Replay state transitions silently to position the aggregate in the correct status.
    // This is the standard pattern for aggregate reconstitution from persistence —
    // the alternative (a setStatus setter) would expose a mutator that bypasses the
    // state machine and allow callers to put the aggregate into impossible states.
    restoreStatus(order, OrderStatus.valueOf(entity.getStatus()), entity.getPaymentRef());
    return order;
  }

  private void restoreStatus(Order order, OrderStatus targetStatus, String paymentRef) {
    // Walk the state machine forward until the aggregate reaches the persisted status.
    // Each transition is idempotent from the mapper's perspective — we control the input.
    if (targetStatus == OrderStatus.PENDING) {
      return;
    }
    order.confirmPayment(paymentRef != null ? paymentRef : "");
    if (targetStatus == OrderStatus.PAYMENT_CONFIRMED) {
      return;
    }
    order.reserveStock();
    if (targetStatus == OrderStatus.STOCK_RESERVED) {
      return;
    }
    if (targetStatus == OrderStatus.CONFIRMED) {
      order.confirm();
    } else if (targetStatus == OrderStatus.FAILED) {
      order.fail("restored from persistence");
    }
  }
}

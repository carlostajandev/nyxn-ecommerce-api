package com.nyxn.ecommerce.solid.orders.application.mappers;

import com.nyxn.ecommerce.solid.orders.application.dto.OrderResponse;
import com.nyxn.ecommerce.solid.orders.domain.Order;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * Manual mapper between the {@link Order} aggregate and {@link OrderResponse} DTO.
 *
 * <p>Manual mapping is preferred here over a generated mapper (MapStruct) because unwrapping value
 * objects ({@code OrderId.value()}, {@code Money.getAmount()}) requires custom logic that MapStruct
 * cannot generate without a complex configuration. Keeping it manual makes the unwrapping explicit
 * and immediately visible to a code reviewer.
 */
@Component
public class OrderDtoMapper {

  public OrderResponse toResponse(Order order) {
    return new OrderResponse(
        order.getId().value(),
        order.getCustomerId(),
        order.getProductId().getValue(),
        order.getQuantity(),
        order.getTotal().getAmount(),
        order.getTotal().getCurrency(),
        order.getStatus().name(),
        order.getPaymentReference(),
        order.getCreatedAt());
  }

  public Page<OrderResponse> toResponsePage(Page<Order> page) {
    return page.map(this::toResponse);
  }
}

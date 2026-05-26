package com.nyxn.ecommerce.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nyxn.ecommerce.application.dto.CreateProductCommand;
import com.nyxn.ecommerce.application.usecases.CreateProductUseCaseImpl;
import com.nyxn.ecommerce.domain.exceptions.InvalidMoneyException;
import com.nyxn.ecommerce.domain.model.Product;
import com.nyxn.ecommerce.domain.ports.out.ProductEventPublisher;
import com.nyxn.ecommerce.domain.ports.out.ProductRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreateProductUseCaseTest {

  @Mock private ProductRepository productRepository;
  @Mock private ProductEventPublisher eventPublisher;

  @InjectMocks private CreateProductUseCaseImpl createProductUseCase;

  @Test
  void execute_whenValidCommand_thenProductIsCreatedAndEventPublished() {
    CreateProductCommand command =
        new CreateProductCommand(
            "Gaming Laptop", "High-end laptop", new BigDecimal("1299.99"), 50, "ELECTRONICS");

    when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

    Product result = createProductUseCase.execute(command);

    assertThat(result.getName()).isEqualTo("Gaming Laptop");
    assertThat(result.getPrice().getAmount()).isEqualByComparingTo("1299.99");
    assertThat(result.getStock().getQuantity()).isEqualTo(50);
    assertThat(result.getId()).isNotNull();

    verify(productRepository).save(any(Product.class));
    verify(eventPublisher).publishProductCreated(any(Product.class));
  }

  @Test
  void execute_whenPriceIsNegative_thenThrowInvalidMoneyException() {
    CreateProductCommand command =
        new CreateProductCommand("Bad Product", "desc", new BigDecimal("-5.00"), 10, "ELECTRONICS");

    assertThatThrownBy(() -> createProductUseCase.execute(command))
        .isInstanceOf(InvalidMoneyException.class)
        .hasMessageContaining("Price cannot be negative");
  }
}

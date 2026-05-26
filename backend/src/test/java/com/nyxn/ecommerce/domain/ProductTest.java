package com.nyxn.ecommerce.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nyxn.ecommerce.domain.exceptions.InsufficientStockException;
import com.nyxn.ecommerce.domain.exceptions.InvalidMoneyException;
import com.nyxn.ecommerce.domain.model.Product;
import com.nyxn.ecommerce.domain.valueobject.Money;
import com.nyxn.ecommerce.domain.valueobject.ProductId;
import com.nyxn.ecommerce.domain.valueobject.Stock;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ProductTest {

  private static Product buildProduct() {
    return Product.builder()
        .id(ProductId.generate())
        .name("Test Product")
        .description("A test product")
        .price(Money.ofUSD(new BigDecimal("99.99")))
        .stock(Stock.of(100))
        .category("ELECTRONICS")
        .build();
  }

  @Test
  void decreaseStock_whenQuantityIsAvailable_thenStockDecreases() {
    Product product = buildProduct();

    product.decreaseStock(10);

    assertThat(product.getStock().getQuantity()).isEqualTo(90);
  }

  @Test
  void decreaseStock_whenQuantityExceedsStock_thenThrowInsufficientStockException() {
    Product product = buildProduct();

    assertThatThrownBy(() -> product.decreaseStock(200))
        .isInstanceOf(InsufficientStockException.class)
        .hasMessageContaining("Cannot decrease stock");
  }

  @Test
  void increaseStock_whenQuantityIsPositive_thenStockIncreases() {
    Product product = buildProduct();

    product.increaseStock(50);

    assertThat(product.getStock().getQuantity()).isEqualTo(150);
  }

  @Test
  void isLowStock_whenStockBelowTen_thenReturnTrue() {
    Product product =
        Product.builder()
            .id(ProductId.generate())
            .name("Low Stock Product")
            .description("desc")
            .price(Money.ofUSD(BigDecimal.TEN))
            .stock(Stock.of(5))
            .category("MISC")
            .build();

    assertThat(product.isLowStock()).isTrue();
  }

  @Test
  void money_whenNegativeAmount_thenThrowInvalidMoneyException() {
    assertThatThrownBy(() -> Money.ofUSD(new BigDecimal("-1.00")))
        .isInstanceOf(InvalidMoneyException.class)
        .hasMessageContaining("Price cannot be negative");
  }

  @Test
  void money_whenSumTwoDifferentCurrencies_thenThrowInvalidMoneyException() {
    Money usd = Money.ofUSD(BigDecimal.TEN);
    Money eur = Money.of(BigDecimal.TEN, "EUR");

    assertThatThrownBy(() -> usd.add(eur))
        .isInstanceOf(InvalidMoneyException.class)
        .hasMessageContaining("Currency mismatch");
  }

  @Test
  void product_whenBuiltWithoutRequiredFields_thenThrowNullPointerException() {
    assertThatThrownBy(() -> Product.builder().build())
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("id is required");
  }
}

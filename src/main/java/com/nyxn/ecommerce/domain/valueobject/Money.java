package com.nyxn.ecommerce.domain.valueobject;

import com.nyxn.ecommerce.domain.exceptions.InvalidMoneyException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Value object que representa dinero con precisión monetaria. Usando BigDecimal con HALF_EVEN
 * (banker's rounding) para evitar acumulación de errores en descuentos, impuestos y totales. Elegí
 * BigDecimal sobre double: double tiene errores de representación binaria inaceptables en contextos
 * financieros.
 */
public final class Money {

  private static final int SCALE = 2;
  private static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

  private final BigDecimal amount;
  private final String currency;

  private Money(BigDecimal amount, String currency) {
    if (amount.compareTo(BigDecimal.ZERO) < 0) {
      throw new InvalidMoneyException("Price cannot be negative: " + amount);
    }
    this.amount = amount.setScale(SCALE, ROUNDING);
    this.currency = Objects.requireNonNull(currency, "Currency cannot be null");
  }

  public static Money of(BigDecimal amount, String currency) {
    return new Money(amount, currency);
  }

  public static Money ofUSD(BigDecimal amount) {
    return new Money(amount, "USD");
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getCurrency() {
    return currency;
  }

  public Money add(Money other) {
    assertSameCurrency(other);
    return new Money(this.amount.add(other.amount), this.currency);
  }

  public Money subtract(Money other) {
    assertSameCurrency(other);
    return new Money(this.amount.subtract(other.amount), this.currency);
  }

  private void assertSameCurrency(Money other) {
    if (!this.currency.equals(other.currency)) {
      throw new InvalidMoneyException(
          "Currency mismatch: " + this.currency + " vs " + other.currency);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Money other)) return false;
    return amount.compareTo(other.amount) == 0 && currency.equals(other.currency);
  }

  @Override
  public int hashCode() {
    return Objects.hash(amount.stripTrailingZeros(), currency);
  }

  @Override
  public String toString() {
    return amount + " " + currency;
  }
}

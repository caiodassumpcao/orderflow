package com.caiodassumpcao.orderflow.orders.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/** Valor monetário não negativo com duas casas decimais. */
public record Money(BigDecimal amount, Currency currency) {

  public static final int SCALE = 2;

  public Money {
    Objects.requireNonNull(currency, "currency is required");
    if (amount == null) {
      throw new InvalidOrderException("Amount is required");
    }
    if (amount.signum() < 0) {
      throw new InvalidOrderException("Amount must not be negative");
    }
    if (amount.stripTrailingZeros().scale() > SCALE) {
      throw new InvalidOrderException("Amount must have at most " + SCALE + " decimal places");
    }
    amount = amount.setScale(SCALE, RoundingMode.UNNECESSARY);
  }

  public static Money zero(Currency currency) {
    return new Money(BigDecimal.ZERO, currency);
  }

  public Money plus(Money other) {
    requireSameCurrency(other);
    return new Money(amount.add(other.amount), currency);
  }

  public Money times(int factor) {
    if (factor < 0) {
      throw new InvalidOrderException("Factor must not be negative");
    }
    return new Money(amount.multiply(BigDecimal.valueOf(factor)), currency);
  }

  private void requireSameCurrency(Money other) {
    if (!currency.equals(other.currency)) {
      throw new InvalidOrderException(
          "Currency mismatch: "
              + currency.getCurrencyCode()
              + " and "
              + other.currency.getCurrencyCode());
    }
  }

  @Override
  public String toString() {
    return amount.toPlainString() + " " + currency.getCurrencyCode();
  }
}

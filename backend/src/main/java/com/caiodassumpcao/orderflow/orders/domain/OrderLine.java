package com.caiodassumpcao.orderflow.orders.domain;

import java.math.BigDecimal;
import java.util.Objects;

/** Item do pedido: produto, quantidade e preço unitário. */
public record OrderLine(Sku sku, int quantity, Money unitPrice) {

  public static final int MAX_QUANTITY = 1_000;

  /** Limite que mantém o total do pedido dentro da precisão persistida. */
  public static final BigDecimal MAX_UNIT_PRICE = new BigDecimal("1000000.00");

  public OrderLine {
    Objects.requireNonNull(sku, "sku is required");
    Objects.requireNonNull(unitPrice, "unit price is required");
    if (quantity < 1 || quantity > MAX_QUANTITY) {
      throw new InvalidOrderException("Quantity must be between 1 and " + MAX_QUANTITY);
    }
    if (unitPrice.amount().signum() <= 0) {
      throw new InvalidOrderException("Unit price must be positive");
    }
    if (unitPrice.amount().compareTo(MAX_UNIT_PRICE) > 0) {
      throw new InvalidOrderException("Unit price must not exceed " + MAX_UNIT_PRICE);
    }
  }

  public Money total() {
    return unitPrice.times(quantity);
  }
}

package com.caiodassumpcao.orderflow.inventory.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Item pedido: produto, quantidade e o preço unitário informado no pedido (conferido com o
 * catálogo).
 */
public record ReservationLine(Sku sku, int quantity, BigDecimal unitPrice) {

  public ReservationLine {
    Objects.requireNonNull(sku, "sku");
    Objects.requireNonNull(unitPrice, "unitPrice");
    if (quantity < 1) {
      throw new IllegalArgumentException("Quantity must be positive");
    }
  }
}

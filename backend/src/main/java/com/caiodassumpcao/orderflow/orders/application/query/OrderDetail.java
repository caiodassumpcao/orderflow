package com.caiodassumpcao.orderflow.orders.application.query;

import java.math.BigDecimal;
import java.util.List;

/** Detalhe do pedido (modelo de leitura). */
public record OrderDetail(OrderSummary summary, List<Item> items, String cancellationReason) {

  public OrderDetail {
    items = List.copyOf(items);
  }

  /** Item do pedido. */
  public record Item(String sku, int quantity, BigDecimal unitPrice) {

    public BigDecimal total() {
      return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
  }
}

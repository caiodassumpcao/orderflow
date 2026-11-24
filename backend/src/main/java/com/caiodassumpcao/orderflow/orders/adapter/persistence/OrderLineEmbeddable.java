package com.caiodassumpcao.orderflow.orders.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;

/** Item do pedido na tabela {@code command.order_lines}. */
@Embeddable
class OrderLineEmbeddable {

  @Column(name = "sku", nullable = false, length = 32)
  private String sku;

  @Column(name = "quantity", nullable = false)
  private int quantity;

  @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
  private BigDecimal unitPrice;

  protected OrderLineEmbeddable() {}

  OrderLineEmbeddable(String sku, int quantity, BigDecimal unitPrice) {
    this.sku = sku;
    this.quantity = quantity;
    this.unitPrice = unitPrice;
  }

  String sku() {
    return sku;
  }

  int quantity() {
    return quantity;
  }

  BigDecimal unitPrice() {
    return unitPrice;
  }
}

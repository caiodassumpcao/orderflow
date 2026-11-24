package com.caiodassumpcao.orderflow.orders.adapter.web;

import com.caiodassumpcao.orderflow.orders.domain.Order;
import com.caiodassumpcao.orderflow.orders.domain.OrderLine;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Estado do pedido devolvido pelos comandos (modelo de escrita). */
record OrderResponse(
    UUID id,
    String status,
    String currency,
    BigDecimal total,
    List<Item> items,
    String cancellationReason,
    Instant createdAt,
    Instant updatedAt) {

  static OrderResponse of(Order order) {
    return new OrderResponse(
        order.id().value(),
        order.status().name(),
        order.currency().getCurrencyCode(),
        order.total().amount(),
        order.lines().stream().map(Item::of).toList(),
        order.cancellationReason().orElse(null),
        order.createdAt(),
        order.updatedAt());
  }

  /** Item do pedido. */
  record Item(String sku, int quantity, BigDecimal unitPrice, BigDecimal total) {

    static Item of(OrderLine line) {
      return new Item(
          line.sku().value(), line.quantity(), line.unitPrice().amount(), line.total().amount());
    }
  }
}

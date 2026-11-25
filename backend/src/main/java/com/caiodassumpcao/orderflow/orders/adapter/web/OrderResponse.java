package com.caiodassumpcao.orderflow.orders.adapter.web;

import com.caiodassumpcao.orderflow.orders.application.query.OrderDetail;
import com.caiodassumpcao.orderflow.orders.application.query.OrderSummary;
import com.caiodassumpcao.orderflow.orders.domain.Order;
import com.caiodassumpcao.orderflow.orders.domain.OrderLine;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Estado completo do pedido: resposta dos comandos (modelo de escrita) e do detalhe (modelo de
 * leitura), com o mesmo formato.
 */
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

  static OrderResponse of(OrderDetail detail) {
    OrderSummary summary = detail.summary();
    return new OrderResponse(
        summary.id().value(),
        summary.status().name(),
        summary.currency().getCurrencyCode(),
        summary.total(),
        detail.items().stream()
            .map(item -> new Item(item.sku(), item.quantity(), item.unitPrice(), item.total()))
            .toList(),
        detail.cancellationReason(),
        summary.createdAt(),
        summary.updatedAt());
  }

  /** Item do pedido. */
  record Item(String sku, int quantity, BigDecimal unitPrice, BigDecimal total) {

    static Item of(OrderLine line) {
      return new Item(
          line.sku().value(), line.quantity(), line.unitPrice().amount(), line.total().amount());
    }
  }
}

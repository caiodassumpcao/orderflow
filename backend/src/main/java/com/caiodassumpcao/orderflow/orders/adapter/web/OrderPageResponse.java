package com.caiodassumpcao.orderflow.orders.adapter.web;

import com.caiodassumpcao.orderflow.orders.application.query.OrderPage;
import com.caiodassumpcao.orderflow.orders.application.query.OrderSummary;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Página da listagem de pedidos.
 *
 * @param items pedidos, do mais novo para o mais antigo
 * @param nextCursor valor para o parâmetro {@code cursor} da próxima página; nulo na última
 */
record OrderPageResponse(List<Summary> items, String nextCursor) {

  static OrderPageResponse of(OrderPage page) {
    return new OrderPageResponse(
        page.items().stream().map(Summary::of).toList(),
        page.next().map(PageCursorCodec::encode).orElse(null));
  }

  /** Linha da listagem. */
  record Summary(
      UUID id,
      String status,
      String currency,
      BigDecimal total,
      int itemCount,
      Instant createdAt,
      Instant updatedAt) {

    static Summary of(OrderSummary summary) {
      return new Summary(
          summary.id().value(),
          summary.status().name(),
          summary.currency().getCurrencyCode(),
          summary.total(),
          summary.itemCount(),
          summary.createdAt(),
          summary.updatedAt());
    }
  }
}

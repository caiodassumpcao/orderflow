package com.caiodassumpcao.orderflow.orders.application.query;

import com.caiodassumpcao.orderflow.orders.domain.OrderId;
import java.time.Instant;
import java.util.Objects;

/**
 * Posição na listagem (paginação keyset): a próxima página começa logo depois do pedido com este
 * {@code createdAt} e {@code id}, na ordem decrescente de criação.
 */
public record PageCursor(Instant createdAt, OrderId orderId) {

  public PageCursor {
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(orderId, "orderId");
  }

  static PageCursor after(OrderSummary summary) {
    return new PageCursor(summary.createdAt(), summary.id());
  }
}

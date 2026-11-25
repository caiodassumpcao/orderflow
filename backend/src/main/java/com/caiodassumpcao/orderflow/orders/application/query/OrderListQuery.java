package com.caiodassumpcao.orderflow.orders.application.query;

import com.caiodassumpcao.orderflow.orders.domain.CustomerId;
import com.caiodassumpcao.orderflow.orders.domain.OrderStatus;
import java.util.Objects;
import java.util.Optional;

/**
 * Filtros da listagem de pedidos de um cliente.
 *
 * @param customerId cliente autenticado (só os pedidos dele são listados)
 * @param status filtro opcional por status
 * @param limit tamanho da página, de 1 a {@value #MAX_LIMIT}
 * @param after cursor da página anterior; vazio para a primeira
 */
public record OrderListQuery(
    CustomerId customerId, Optional<OrderStatus> status, int limit, Optional<PageCursor> after) {

  public static final int DEFAULT_LIMIT = 20;
  public static final int MAX_LIMIT = 100;

  public OrderListQuery {
    Objects.requireNonNull(customerId, "customerId");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(after, "after");
    if (limit < 1 || limit > MAX_LIMIT) {
      throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
    }
  }
}

package com.caiodassumpcao.orderflow.orders.application.query;

import com.caiodassumpcao.orderflow.orders.domain.CustomerId;
import com.caiodassumpcao.orderflow.orders.domain.OrderStatus;
import java.util.Objects;
import java.util.Optional;

/**
 * Filtros da listagem de todos os pedidos (visão de administrador).
 *
 * @param customerId filtro opcional por cliente
 * @param status filtro opcional por status
 * @param limit tamanho da página, de 1 a {@link OrderListQuery#MAX_LIMIT}
 * @param after cursor da página anterior; vazio para a primeira
 */
public record AdminOrderListQuery(
    Optional<CustomerId> customerId,
    Optional<OrderStatus> status,
    int limit,
    Optional<PageCursor> after) {

  public AdminOrderListQuery {
    Objects.requireNonNull(customerId, "customerId");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(after, "after");
    if (limit < 1 || limit > OrderListQuery.MAX_LIMIT) {
      throw new IllegalArgumentException("limit must be between 1 and " + OrderListQuery.MAX_LIMIT);
    }
  }
}

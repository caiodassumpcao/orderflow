package com.caiodassumpcao.orderflow.orders.application.query;

import java.util.List;
import java.util.Optional;

/**
 * Página da listagem.
 *
 * @param items pedidos da página, do mais novo para o mais antigo
 * @param next cursor da próxima página; vazio na última
 */
public record OrderPage(List<OrderSummary> items, Optional<PageCursor> next) {

  public OrderPage {
    items = List.copyOf(items);
  }
}

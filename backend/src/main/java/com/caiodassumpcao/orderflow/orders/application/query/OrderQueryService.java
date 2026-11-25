package com.caiodassumpcao.orderflow.orders.application.query;

import com.caiodassumpcao.orderflow.orders.application.OrderNotFoundException;
import com.caiodassumpcao.orderflow.orders.application.port.OrderReadModel;
import com.caiodassumpcao.orderflow.orders.domain.CustomerId;
import com.caiodassumpcao.orderflow.orders.domain.OrderId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Consultas de pedidos sobre o modelo de leitura. Eventualmente consistente com o modelo de
 * escrita: um pedido recém-criado pode levar alguns instantes para aparecer.
 */
public class OrderQueryService {

  private final OrderReadModel readModel;

  public OrderQueryService(OrderReadModel readModel) {
    this.readModel = Objects.requireNonNull(readModel);
  }

  /** Uma página de pedidos do cliente, com o cursor da próxima quando houver. */
  public OrderPage listOrders(OrderListQuery query) {
    // Busca um item a mais só para saber se existe próxima página.
    List<OrderSummary> rows =
        readModel.findOrders(query.customerId(), query.status(), query.after(), query.limit() + 1);
    if (rows.size() <= query.limit()) {
      return new OrderPage(rows, Optional.empty());
    }
    List<OrderSummary> page = rows.subList(0, query.limit());
    return new OrderPage(page, Optional.of(PageCursor.after(page.getLast())));
  }

  /**
   * Detalhe de um pedido do cliente.
   *
   * @throws OrderNotFoundException se o pedido não existe, ainda não foi projetado ou é de outro
   *     cliente
   */
  public OrderDetail getOrder(OrderId orderId, CustomerId requester) {
    return readModel
        .findOrder(orderId, requester)
        .orElseThrow(() -> new OrderNotFoundException(orderId));
  }
}

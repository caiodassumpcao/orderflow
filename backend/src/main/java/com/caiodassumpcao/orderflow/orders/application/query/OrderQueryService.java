package com.caiodassumpcao.orderflow.orders.application.query;

import com.caiodassumpcao.orderflow.orders.application.OrderNotFoundException;
import com.caiodassumpcao.orderflow.orders.application.port.OrderReadModel;
import com.caiodassumpcao.orderflow.orders.domain.CustomerId;
import com.caiodassumpcao.orderflow.orders.domain.OrderId;
import com.caiodassumpcao.orderflow.orders.domain.OrderStatus;
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

  /** Uma página dos pedidos do cliente, com o cursor da próxima quando houver. */
  public OrderPage listOrders(OrderListQuery query) {
    return page(Optional.of(query.customerId()), query.status(), query.after(), query.limit());
  }

  /** Uma página de todos os pedidos (visão de administrador). */
  public OrderPage listAllOrders(AdminOrderListQuery query) {
    return page(query.customerId(), query.status(), query.after(), query.limit());
  }

  /**
   * Detalhe de um pedido do cliente.
   *
   * @throws OrderNotFoundException se o pedido não existe, ainda não foi projetado ou é de outro
   *     cliente
   */
  public OrderDetail getOrder(OrderId orderId, CustomerId requester) {
    return readModel
        .findOrder(orderId, Optional.of(requester))
        .orElseThrow(() -> new OrderNotFoundException(orderId));
  }

  /**
   * Detalhe de qualquer pedido (visão de administrador).
   *
   * @throws OrderNotFoundException se o pedido não existe ou ainda não foi projetado
   */
  public OrderDetail getAnyOrder(OrderId orderId) {
    return readModel
        .findOrder(orderId, Optional.empty())
        .orElseThrow(() -> new OrderNotFoundException(orderId));
  }

  private OrderPage page(
      Optional<CustomerId> customerId,
      Optional<OrderStatus> status,
      Optional<PageCursor> after,
      int limit) {
    // Busca um item a mais só para saber se existe próxima página.
    List<OrderSummary> rows = readModel.findOrders(customerId, status, after, limit + 1);
    if (rows.size() <= limit) {
      return new OrderPage(rows, Optional.empty());
    }
    List<OrderSummary> page = rows.subList(0, limit);
    return new OrderPage(page, Optional.of(PageCursor.after(page.getLast())));
  }
}

package com.caiodassumpcao.orderflow.orders.application.port;

import com.caiodassumpcao.orderflow.orders.application.query.OrderDetail;
import com.caiodassumpcao.orderflow.orders.application.query.OrderSummary;
import com.caiodassumpcao.orderflow.orders.application.query.PageCursor;
import com.caiodassumpcao.orderflow.orders.domain.CustomerId;
import com.caiodassumpcao.orderflow.orders.domain.OrderId;
import com.caiodassumpcao.orderflow.orders.domain.OrderStatus;
import java.util.List;
import java.util.Optional;

/**
 * Consultas ao modelo de leitura. Pedidos ainda sem o {@code OrderCreated} projetado não aparecem.
 */
public interface OrderReadModel {

  /**
   * Pedidos do cliente do mais novo para o mais antigo, começando depois de {@code after}.
   *
   * @param limit quantidade máxima de linhas devolvidas
   */
  List<OrderSummary> findOrders(
      CustomerId customerId, Optional<OrderStatus> status, Optional<PageCursor> after, int limit);

  /** Detalhe do pedido, se ele existir e pertencer ao cliente. */
  Optional<OrderDetail> findOrder(OrderId orderId, CustomerId customerId);
}

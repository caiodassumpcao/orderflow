package com.caiodassumpcao.orderflow.orders.application;

import com.caiodassumpcao.orderflow.orders.domain.OrderId;

/**
 * Pedido inexistente ou que o solicitante não pode ver. Os dois casos são indistinguíveis de
 * propósito, para não revelar a existência de pedidos de outros clientes.
 */
public class OrderNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public OrderNotFoundException(OrderId orderId) {
    super("Order " + orderId + " not found");
  }
}

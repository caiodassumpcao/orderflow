package com.caiodassumpcao.orderflow.orders.application.port;

import com.caiodassumpcao.orderflow.orders.domain.OrderId;

/** O pedido foi alterado por outra transação desde que foi lido (conflito de versão). */
public class ConcurrentOrderUpdateException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ConcurrentOrderUpdateException(OrderId orderId, Throwable cause) {
    super("Order " + orderId + " was modified concurrently", cause);
  }
}

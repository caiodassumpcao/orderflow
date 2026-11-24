package com.caiodassumpcao.orderflow.orders.domain;

/** A operação não é permitida no estado atual do pedido (transição inválida). */
public class IllegalOrderStateException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final OrderStatus currentStatus;
  private final String action;

  public IllegalOrderStateException(OrderStatus currentStatus, String action) {
    super("Cannot " + action + " an order in status " + currentStatus);
    this.currentStatus = currentStatus;
    this.action = action;
  }

  public OrderStatus currentStatus() {
    return currentStatus;
  }

  public String action() {
    return action;
  }
}

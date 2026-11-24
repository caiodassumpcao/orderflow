package com.caiodassumpcao.orderflow.orders.domain;

/**
 * Ciclo de vida do pedido: {@code CREATED → STOCK_RESERVED → CONFIRMED → SHIPPED}, com cancelamento
 * possível a partir de {@code CREATED} ou {@code STOCK_RESERVED}.
 */
public enum OrderStatus {
  CREATED,
  STOCK_RESERVED,
  CONFIRMED,
  SHIPPED,
  CANCELLED;

  boolean isCancellable() {
    return this == CREATED || this == STOCK_RESERVED;
  }
}

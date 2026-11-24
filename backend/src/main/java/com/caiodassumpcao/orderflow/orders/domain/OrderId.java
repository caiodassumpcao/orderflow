package com.caiodassumpcao.orderflow.orders.domain;

import java.util.Objects;
import java.util.UUID;

/** Identidade do pedido. */
public record OrderId(UUID value) {

  public OrderId {
    Objects.requireNonNull(value, "order id is required");
  }

  @Override
  public String toString() {
    return value.toString();
  }
}

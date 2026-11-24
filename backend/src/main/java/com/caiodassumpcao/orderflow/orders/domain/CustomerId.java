package com.caiodassumpcao.orderflow.orders.domain;

import java.util.regex.Pattern;

/** Cliente dono do pedido: o {@code sub} do token emitido pelo provedor de identidade. */
public record CustomerId(String value) {

  private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9._:@-]{1,64}$");

  public CustomerId {
    if (value == null || !VALID.matcher(value).matches()) {
      throw new InvalidOrderException("Customer id must have 1 to 64 safe characters");
    }
  }

  @Override
  public String toString() {
    return value;
  }
}

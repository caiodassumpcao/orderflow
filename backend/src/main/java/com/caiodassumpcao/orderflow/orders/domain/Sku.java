package com.caiodassumpcao.orderflow.orders.domain;

import java.util.regex.Pattern;

/** Código do produto: 3 a 32 caracteres, letras maiúsculas, dígitos e hífen. */
public record Sku(String value) {

  private static final Pattern VALID = Pattern.compile("^[A-Z0-9][A-Z0-9-]{2,31}$");

  public Sku {
    if (value == null || !VALID.matcher(value).matches()) {
      throw new InvalidOrderException(
          "SKU must have 3 to 32 uppercase letters, digits or hyphens: " + value);
    }
  }

  @Override
  public String toString() {
    return value;
  }
}

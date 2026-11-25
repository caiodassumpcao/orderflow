package com.caiodassumpcao.orderflow.inventory.domain;

import java.util.regex.Pattern;

/**
 * Código do produto no inventário. Mesmo formato do contexto de pedidos, mas tipo próprio: os
 * contextos só compartilham os contratos de evento (ADR-0001).
 */
public record Sku(String value) implements Comparable<Sku> {

  private static final Pattern VALID = Pattern.compile("^[A-Z0-9][A-Z0-9-]{2,31}$");

  public Sku {
    if (value == null || !VALID.matcher(value).matches()) {
      throw new IllegalArgumentException("Invalid SKU: " + value);
    }
  }

  @Override
  public int compareTo(Sku other) {
    return value.compareTo(other.value);
  }

  @Override
  public String toString() {
    return value;
  }
}

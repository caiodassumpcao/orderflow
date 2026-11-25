package com.caiodassumpcao.orderflow.inventory.application;

import com.caiodassumpcao.orderflow.inventory.domain.Sku;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/** Dados de um produto novo no catálogo. */
public record RegisterProductCommand(
    Sku sku, String name, Currency currency, BigDecimal unitPrice, int initialUnits) {

  public RegisterProductCommand {
    Objects.requireNonNull(sku, "sku");
    Objects.requireNonNull(currency, "currency");
  }
}

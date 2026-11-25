package com.caiodassumpcao.orderflow.inventory.application;

import com.caiodassumpcao.orderflow.inventory.domain.Sku;

/** Produto inexistente no catálogo. */
public class StockItemNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public StockItemNotFoundException(Sku sku) {
    super("Stock item " + sku + " not found");
  }
}

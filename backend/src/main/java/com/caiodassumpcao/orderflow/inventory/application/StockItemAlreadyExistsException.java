package com.caiodassumpcao.orderflow.inventory.application;

import com.caiodassumpcao.orderflow.inventory.domain.Sku;

/** Já existe produto cadastrado com o SKU. */
public class StockItemAlreadyExistsException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public StockItemAlreadyExistsException(Sku sku) {
    super("Stock item " + sku + " already exists");
  }
}

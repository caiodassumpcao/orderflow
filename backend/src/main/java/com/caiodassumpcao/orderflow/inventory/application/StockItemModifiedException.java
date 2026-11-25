package com.caiodassumpcao.orderflow.inventory.application;

import com.caiodassumpcao.orderflow.inventory.domain.Sku;

/** O produto mudou desde a versão informada pelo cliente (concorrência otimista). */
public class StockItemModifiedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final long currentVersion;

  public StockItemModifiedException(Sku sku, long currentVersion) {
    super("Stock item " + sku + " changed; reload it and retry");
    this.currentVersion = currentVersion;
  }

  public long currentVersion() {
    return currentVersion;
  }
}

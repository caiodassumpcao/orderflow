package com.caiodassumpcao.orderflow.inventory.domain;

/** A reposição deixaria o produto com mais unidades livres do que o teto permitido. */
public class StockLimitExceededException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public StockLimitExceededException(Sku sku, int limit) {
    super("Restocking " + sku + " would exceed " + limit + " available units");
  }
}

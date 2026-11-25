package com.caiodassumpcao.orderflow.inventory.domain;

/** Por que o inventário recusou um pedido. */
public enum RejectionReason {
  /** Algum produto não tem quantidade livre suficiente. */
  OUT_OF_STOCK,
  /** Algum produto não existe no catálogo. */
  UNKNOWN_SKU,
  /** O preço ou a moeda do pedido diferem do catálogo. */
  PRICE_MISMATCH
}

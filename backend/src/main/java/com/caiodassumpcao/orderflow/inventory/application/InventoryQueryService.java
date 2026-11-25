package com.caiodassumpcao.orderflow.inventory.application;

import com.caiodassumpcao.orderflow.inventory.application.port.StockItemRepository;
import com.caiodassumpcao.orderflow.inventory.domain.Sku;
import com.caiodassumpcao.orderflow.inventory.domain.StockItem;
import java.util.Objects;

/** Consulta de estoque por produto. */
public class InventoryQueryService {

  private final StockItemRepository stockItems;

  public InventoryQueryService(StockItemRepository stockItems) {
    this.stockItems = Objects.requireNonNull(stockItems);
  }

  /**
   * Preço e quantidades atuais do produto.
   *
   * @throws StockItemNotFoundException se o SKU não existe
   */
  public StockItem getStock(Sku sku) {
    return stockItems.find(sku).orElseThrow(() -> new StockItemNotFoundException(sku));
  }
}

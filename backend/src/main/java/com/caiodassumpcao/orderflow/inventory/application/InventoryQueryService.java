package com.caiodassumpcao.orderflow.inventory.application;

import com.caiodassumpcao.orderflow.inventory.application.port.StockItemRepository;
import com.caiodassumpcao.orderflow.inventory.domain.Sku;
import com.caiodassumpcao.orderflow.inventory.domain.StockItem;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Consultas de estoque e do catálogo de produtos. */
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

  /**
   * Uma página de produtos em ordem de SKU (paginação keyset pelo próprio SKU).
   *
   * @param after último SKU da página anterior
   * @param limit tamanho da página, de 1 a {@link StockPage#MAX_LIMIT}
   */
  public StockPage listStock(Optional<Sku> after, int limit) {
    if (limit < 1 || limit > StockPage.MAX_LIMIT) {
      throw new IllegalArgumentException("Limit must be between 1 and " + StockPage.MAX_LIMIT);
    }
    // Busca um item a mais só para saber se existe próxima página.
    List<StockItem> rows = stockItems.findPage(after, limit + 1);
    if (rows.size() <= limit) {
      return new StockPage(rows, Optional.empty());
    }
    List<StockItem> page = rows.subList(0, limit);
    return new StockPage(page, Optional.of(page.getLast().sku()));
  }
}

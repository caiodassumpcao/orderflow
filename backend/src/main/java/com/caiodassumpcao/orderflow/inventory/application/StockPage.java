package com.caiodassumpcao.orderflow.inventory.application;

import com.caiodassumpcao.orderflow.inventory.domain.Sku;
import com.caiodassumpcao.orderflow.inventory.domain.StockItem;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Página de produtos em ordem de SKU.
 *
 * @param next SKU a partir do qual buscar a próxima página; vazio na última
 */
public record StockPage(List<StockItem> items, Optional<Sku> next) {

  public static final int DEFAULT_LIMIT = 50;
  public static final int MAX_LIMIT = 100;

  public StockPage {
    items = List.copyOf(items);
    Objects.requireNonNull(next, "next");
  }
}

package com.caiodassumpcao.orderflow.inventory.application;

import com.caiodassumpcao.orderflow.inventory.application.port.StockItemRepository;
import com.caiodassumpcao.orderflow.inventory.domain.Sku;
import com.caiodassumpcao.orderflow.inventory.domain.StockItem;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Estoque em memória que se comporta como o banco: devolve cópias, grava com verificação de versão
 * e incrementa a versão a cada gravação.
 */
final class InMemoryStockItemRepository implements StockItemRepository {

  private final NavigableMap<Sku, StockItem> rows = new TreeMap<>();

  /** Grava a linha como está (preparação do teste). */
  void put(StockItem item) {
    rows.put(item.sku(), copy(item, item.version()));
  }

  /** Linha atual, como o banco a guarda. */
  StockItem get(Sku sku) {
    return rows.get(sku);
  }

  @Override
  public Optional<StockItem> find(Sku sku) {
    return Optional.ofNullable(rows.get(sku)).map(item -> copy(item, item.version()));
  }

  @Override
  public List<StockItem> findPage(Optional<Sku> after, int limit) {
    NavigableMap<Sku, StockItem> view = after.map(sku -> rows.tailMap(sku, false)).orElse(rows);
    return view.values().stream().limit(limit).map(item -> copy(item, item.version())).toList();
  }

  @Override
  public boolean add(StockItem item) {
    return rows.putIfAbsent(item.sku(), copy(item, item.version())) == null;
  }

  @Override
  public Map<Sku, StockItem> lockAll(Collection<Sku> skus) {
    Map<Sku, StockItem> locked = new LinkedHashMap<>();
    skus.stream()
        .sorted()
        .filter(rows::containsKey)
        .forEach(sku -> locked.put(sku, copy(rows.get(sku), rows.get(sku).version())));
    return locked;
  }

  @Override
  public void saveAll(Collection<StockItem> items) {
    for (StockItem item : items) {
      StockItem current = rows.get(item.sku());
      if (current == null || current.version() != item.version()) {
        throw new IllegalStateException("Stock item " + item.sku() + " changed");
      }
      rows.put(item.sku(), copy(item, item.version() + 1));
    }
  }

  private static StockItem copy(StockItem item, long version) {
    return new StockItem(
        item.sku(),
        item.name(),
        item.currency(),
        item.unitPrice(),
        item.available(),
        item.reserved(),
        version);
  }
}

package com.caiodassumpcao.orderflow.inventory.application.port;

import com.caiodassumpcao.orderflow.inventory.domain.Sku;
import com.caiodassumpcao.orderflow.inventory.domain.StockItem;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Produtos em estoque. */
public interface StockItemRepository {

  Optional<StockItem> find(Sku sku);

  /**
   * Produtos em ordem de SKU, começando depois de {@code after}.
   *
   * @param limit quantidade máxima de produtos devolvidos
   */
  List<StockItem> findPage(Optional<Sku> after, int limit);

  /**
   * Cadastra um produto novo.
   *
   * @return {@code false} se já existe produto com o mesmo SKU (nada é alterado)
   */
  boolean add(StockItem item);

  /**
   * Trava os produtos para atualização até o fim da transação, sempre na mesma ordem (por SKU) para
   * evitar deadlock entre reservas concorrentes. SKUs inexistentes ficam fora do mapa.
   */
  Map<Sku, StockItem> lockAll(Collection<Sku> skus);

  /** Grava as quantidades dos produtos travados. */
  void saveAll(Collection<StockItem> items);
}

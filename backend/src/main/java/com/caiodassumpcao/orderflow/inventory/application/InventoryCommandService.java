package com.caiodassumpcao.orderflow.inventory.application;

import com.caiodassumpcao.orderflow.inventory.application.port.InventoryUnitOfWork;
import com.caiodassumpcao.orderflow.inventory.application.port.StockItemRepository;
import com.caiodassumpcao.orderflow.inventory.domain.Sku;
import com.caiodassumpcao.orderflow.inventory.domain.StockItem;
import java.util.List;
import java.util.Objects;

/**
 * Operação do catálogo (papel ADMIN): cadastro de produtos e reposição de estoque. A reposição usa
 * concorrência otimista: só é aplicada se o produto ainda está na versão que o operador viu, o que
 * também impede que um reenvio da mesma requisição reponha o estoque duas vezes.
 */
public class InventoryCommandService {

  private final StockItemRepository stockItems;
  private final InventoryUnitOfWork unitOfWork;

  public InventoryCommandService(StockItemRepository stockItems, InventoryUnitOfWork unitOfWork) {
    this.stockItems = Objects.requireNonNull(stockItems);
    this.unitOfWork = Objects.requireNonNull(unitOfWork);
  }

  /**
   * Cadastra um produto com as unidades iniciais livres.
   *
   * @throws StockItemAlreadyExistsException se o SKU já está cadastrado
   */
  public StockItem registerProduct(RegisterProductCommand command) {
    StockItem item =
        StockItem.register(
            command.sku(),
            command.name(),
            command.currency(),
            command.unitPrice(),
            command.initialUnits());
    return unitOfWork.inTransaction(() -> {
      if (!stockItems.add(item)) {
        throw new StockItemAlreadyExistsException(command.sku());
      }
      return item;
    });
  }

  /**
   * Repõe unidades livres do produto.
   *
   * @param expectedVersion versão do produto que o operador viu
   * @return o produto depois da reposição, com a nova versão
   * @throws StockItemNotFoundException se o SKU não existe
   * @throws StockItemModifiedException se o produto mudou desde {@code expectedVersion}
   */
  public StockItem restock(Sku sku, int quantity, long expectedVersion) {
    return unitOfWork.inTransaction(() -> {
      StockItem item = stockItems.lockAll(List.of(sku)).get(sku);
      if (item == null) {
        throw new StockItemNotFoundException(sku);
      }
      if (item.version() != expectedVersion) {
        throw new StockItemModifiedException(sku, item.version());
      }
      item.restock(quantity);
      stockItems.saveAll(List.of(item));
      return stockItems.find(sku).orElseThrow(() -> new StockItemNotFoundException(sku));
    });
  }
}

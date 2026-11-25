package com.caiodassumpcao.orderflow.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.caiodassumpcao.orderflow.inventory.application.port.InventoryUnitOfWork;
import com.caiodassumpcao.orderflow.inventory.domain.Sku;
import com.caiodassumpcao.orderflow.inventory.domain.StockItem;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Cadastro, reposição e listagem do catálogo. */
class InventoryCatalogServicesTest {

  private static final Currency BRL = Currency.getInstance("BRL");

  private final InMemoryStockItemRepository stockItems = new InMemoryStockItemRepository();
  private int transactions;
  private final InventoryUnitOfWork unitOfWork =
      new InventoryUnitOfWork() {
        @Override
        public <T> T inTransaction(Supplier<T> work) {
          transactions++;
          return work.get();
        }
      };

  private final InventoryCommandService commands =
      new InventoryCommandService(stockItems, unitOfWork);
  private final InventoryQueryService queries = new InventoryQueryService(stockItems);

  private static StockItem product(String sku, int available, long version) {
    return new StockItem(
        new Sku(sku), "Produto " + sku, BRL, BigDecimal.TEN, available, 0, version);
  }

  @Nested
  class Registration {

    private final RegisterProductCommand command =
        new RegisterProductCommand(
            new Sku("SKU-NEW"), "Garrafa térmica", BRL, new BigDecimal("89.90"), 12);

    @Test
    void addsTheProductToTheCatalog() {
      StockItem registered = commands.registerProduct(command);

      assertThat(registered.version()).isZero();
      assertThat(stockItems.get(new Sku("SKU-NEW")))
          .satisfies(row -> {
            assertThat(row.name()).isEqualTo("Garrafa térmica");
            assertThat(row.available()).isEqualTo(12);
          });
      assertThat(transactions).isOne();
    }

    @Test
    void refusesADuplicateSkuWithoutTouchingTheExistingProduct() {
      stockItems.put(product("SKU-NEW", 3, 4));

      assertThatThrownBy(() -> commands.registerProduct(command))
          .isInstanceOf(StockItemAlreadyExistsException.class)
          .hasMessageContaining("SKU-NEW");
      assertThat(stockItems.get(new Sku("SKU-NEW")).available()).isEqualTo(3);
    }

    @Test
    void validatesTheProductBeforeOpeningATransaction() {
      RegisterProductCommand nameless =
          new RegisterProductCommand(new Sku("SKU-NEW"), " ", BRL, BigDecimal.ONE, 1);

      assertThatThrownBy(() -> commands.registerProduct(nameless))
          .isInstanceOf(IllegalArgumentException.class);
      assertThat(transactions).isZero();
    }
  }

  @Nested
  class Restocking {

    private final Sku sku = new Sku("SKU-001");

    @Test
    void addsUnitsWhenTheVersionMatchesAndReturnsTheNewVersion() {
      stockItems.put(product("SKU-001", 2, 7));

      StockItem restocked = commands.restock(sku, 5, 7);

      assertThat(restocked.available()).isEqualTo(7);
      assertThat(restocked.version()).isEqualTo(8);
      assertThat(stockItems.get(sku).available()).isEqualTo(7);
    }

    @Test
    void refusesAStaleVersionSoARetryNeverRestocksTwice() {
      stockItems.put(product("SKU-001", 2, 7));
      commands.restock(sku, 5, 7);

      assertThatThrownBy(() -> commands.restock(sku, 5, 7))
          .isInstanceOfSatisfying(
              StockItemModifiedException.class, ex -> assertThat(ex.currentVersion()).isEqualTo(8));
      assertThat(stockItems.get(sku).available()).isEqualTo(7);
    }

    @Test
    void reportsUnknownProducts() {
      assertThatThrownBy(() -> commands.restock(sku, 1, 0))
          .isInstanceOf(StockItemNotFoundException.class);
    }
  }

  @Nested
  class Listing {

    @Test
    void pagesThroughTheCatalogInSkuOrder() {
      stockItems.put(product("SKU-003", 1, 0));
      stockItems.put(product("SKU-001", 0, 0));
      stockItems.put(product("SKU-002", 1, 0));

      StockPage first = queries.listStock(Optional.empty(), 2);
      StockPage second = queries.listStock(first.next(), 2);

      assertThat(first.items())
          .extracting(item -> item.sku().value())
          .containsExactly("SKU-001", "SKU-002");
      assertThat(first.next()).contains(new Sku("SKU-002"));
      assertThat(second.items()).extracting(item -> item.sku().value()).containsExactly("SKU-003");
      assertThat(second.next()).isEmpty();
    }

    @Test
    void endsWithoutCursorWhenThePageIsExactlyFull() {
      stockItems.put(product("SKU-001", 1, 0));

      assertThat(queries.listStock(Optional.empty(), 1).next()).isEmpty();
    }

    @Test
    void refusesLimitsOutsideTheRange() {
      assertThatThrownBy(() -> queries.listStock(Optional.empty(), 0))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> queries.listStock(Optional.empty(), StockPage.MAX_LIMIT + 1))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void returnsASingleProduct() {
      stockItems.put(product("SKU-001", 4, 2));

      assertThat(queries.getStock(new Sku("SKU-001")).available()).isEqualTo(4);
      assertThatThrownBy(() -> queries.getStock(new Sku("SKU-404")))
          .isInstanceOf(StockItemNotFoundException.class);
    }
  }
}

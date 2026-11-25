package com.caiodassumpcao.orderflow.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class StockItemTest {

  private static final Currency BRL = Currency.getInstance("BRL");
  private static final Sku SKU = new Sku("SKU-NEW");

  @Nested
  class Registration {

    @Test
    void startsWithTheInitialUnitsFreeAndNothingReserved() {
      StockItem item =
          StockItem.register(SKU, "  Garrafa térmica ", BRL, new BigDecimal("89.9"), 10);

      assertThat(item.name()).isEqualTo("Garrafa térmica");
      assertThat(item.unitPrice()).isEqualByComparingTo("89.90");
      assertThat(item.available()).isEqualTo(10);
      assertThat(item.reserved()).isZero();
      assertThat(item.version()).isZero();
    }

    @Test
    void acceptsAProductWithoutUnitsYet() {
      assertThat(StockItem.register(SKU, "Sem estoque", BRL, BigDecimal.ONE, 0).available())
          .isZero();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "line\nbreak", "tab\there", "nul\u0000"})
    void refusesBlankNamesOrControlCharacters(String name) {
      assertThatThrownBy(() -> StockItem.register(SKU, name, BRL, BigDecimal.ONE, 1))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesNamesLongerThanTheLimit() {
      String longest = "x".repeat(StockItem.MAX_NAME_LENGTH);

      assertThat(StockItem.register(SKU, longest, BRL, BigDecimal.ONE, 1).name()).hasSize(120);
      assertThatThrownBy(() -> StockItem.register(SKU, longest + "x", BRL, BigDecimal.ONE, 1))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesMoreUnitsThanTheCeiling() {
      assertThatThrownBy(() ->
              StockItem.register(SKU, "Muito", BRL, BigDecimal.ONE, StockItem.MAX_AVAILABLE + 1))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  class Restocking {

    @Test
    void addsFreeUnitsAndKeepsTheReservedOnes() {
      StockItem item = new StockItem(SKU, "Produto", BRL, BigDecimal.TEN, 3, 2, 5);

      item.restock(7);

      assertThat(item.available()).isEqualTo(10);
      assertThat(item.reserved()).isEqualTo(2);
    }

    @Test
    void refusesNonPositiveQuantities() {
      StockItem item = new StockItem(SKU, "Produto", BRL, BigDecimal.TEN, 3, 0, 0);

      assertThatThrownBy(() -> item.restock(0)).isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> item.restock(-1)).isInstanceOf(IllegalArgumentException.class);
      assertThat(item.available()).isEqualTo(3);
    }

    @Test
    void neverGoesBeyondTheCeiling() {
      StockItem item =
          new StockItem(SKU, "Produto", BRL, BigDecimal.TEN, StockItem.MAX_AVAILABLE - 1, 0, 0);

      item.restock(1);

      assertThatThrownBy(() -> item.restock(1))
          .isInstanceOf(StockLimitExceededException.class)
          .hasMessageContaining("SKU-NEW");
      assertThat(item.available()).isEqualTo(StockItem.MAX_AVAILABLE);
    }
  }
}

package com.caiodassumpcao.orderflow.orders.domain;

import static com.caiodassumpcao.orderflow.orders.domain.OrderFixtures.BRL;
import static com.caiodassumpcao.orderflow.orders.domain.OrderFixtures.USD;
import static com.caiodassumpcao.orderflow.orders.domain.OrderFixtures.brl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ValueObjectsTest {

  @Nested
  class MoneyBehaviour {

    @Test
    void normalizesTheAmountToTwoDecimalPlaces() {
      assertThat(new Money(new BigDecimal("10"), BRL).amount()).isEqualTo(new BigDecimal("10.00"));
      assertThat(new Money(new BigDecimal("10.500"), BRL)).isEqualTo(brl("10.50"));
    }

    @Test
    void addsAndMultipliesInTheSameCurrency() {
      assertThat(brl("10.25").plus(brl("0.75"))).isEqualTo(brl("11.00"));
      assertThat(brl("19.99").times(3)).isEqualTo(brl("59.97"));
      assertThat(Money.zero(BRL)).isEqualTo(brl("0.00"));
    }

    @Test
    void rejectsArithmeticAcrossCurrencies() {
      Money dollars = new Money(BigDecimal.ONE, USD);

      assertThatThrownBy(() -> brl("1.00").plus(dollars))
          .isInstanceOf(InvalidOrderException.class)
          .hasMessage("Currency mismatch: BRL and USD");
    }

    @Test
    void rejectsInvalidAmounts() {
      assertThatThrownBy(() -> new Money(null, BRL)).isInstanceOf(InvalidOrderException.class);
      assertThatThrownBy(() -> new Money(new BigDecimal("-0.01"), BRL))
          .isInstanceOf(InvalidOrderException.class);
      assertThatThrownBy(() -> new Money(new BigDecimal("1.001"), BRL))
          .isInstanceOf(InvalidOrderException.class)
          .hasMessageContaining("decimal places");
      assertThatThrownBy(() -> brl("1.00").times(-1)).isInstanceOf(InvalidOrderException.class);
    }

    @Test
    void printsAmountAndCurrency() {
      assertThat(brl("5")).hasToString("5.00 BRL");
    }
  }

  @Nested
  class OrderLineBehaviour {

    @Test
    void computesTheLineTotal() {
      assertThat(OrderFixtures.line("SKU-001", 4, "2.50").total()).isEqualTo(brl("10.00"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, OrderLine.MAX_QUANTITY + 1})
    void rejectsQuantityOutOfRange(int quantity) {
      assertThatThrownBy(() -> OrderFixtures.line("SKU-001", quantity, "1.00"))
          .isInstanceOf(InvalidOrderException.class)
          .hasMessageContaining("Quantity");
    }

    @Test
    void rejectsZeroOrExcessiveUnitPrice() {
      assertThatThrownBy(() -> OrderFixtures.line("SKU-001", 1, "0.00"))
          .isInstanceOf(InvalidOrderException.class)
          .hasMessageContaining("positive");
      assertThatThrownBy(() -> OrderFixtures.line("SKU-001", 1, "1000000.01"))
          .isInstanceOf(InvalidOrderException.class)
          .hasMessageContaining("must not exceed");
    }
  }

  @Nested
  class Identifiers {

    @ParameterizedTest
    @ValueSource(strings = {"SKU-001", "ABC", "A1-B2-C3", "X23456789012345678901234567890AB"})
    void acceptsValidSkus(String value) {
      assertThat(new Sku(value)).hasToString(value);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
        strings = {"ab", "sku-001", "-SKU", "SKU 001", "X234567890123456789012345678901AB"})
    void rejectsInvalidSkus(String value) {
      assertThatThrownBy(() -> new Sku(value)).isInstanceOf(InvalidOrderException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"f81d4fae-7dec-11d0-a765-00a0c91e6bf6", "user@example.com", "svc:1"})
    void acceptsSafeCustomerIds(String value) {
      assertThat(new CustomerId(value)).hasToString(value);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"has space", "line\nbreak", "<script>"})
    void rejectsUnsafeCustomerIds(String value) {
      assertThatThrownBy(() -> new CustomerId(value)).isInstanceOf(InvalidOrderException.class);
    }

    @Test
    void rejectsCustomerIdsLongerThan64Characters() {
      assertThatThrownBy(() -> new CustomerId("a".repeat(65)))
          .isInstanceOf(InvalidOrderException.class);
    }

    @Test
    void orderIdRequiresAValueAndPrintsIt() {
      UUID uuid = UUID.randomUUID();

      assertThat(new OrderId(uuid)).hasToString(uuid.toString());
      assertThatThrownBy(() -> new OrderId(null)).isInstanceOf(NullPointerException.class);
    }
  }
}

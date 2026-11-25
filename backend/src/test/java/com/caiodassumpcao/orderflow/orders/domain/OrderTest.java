package com.caiodassumpcao.orderflow.orders.domain;

import static com.caiodassumpcao.orderflow.orders.domain.OrderFixtures.CUSTOMER;
import static com.caiodassumpcao.orderflow.orders.domain.OrderFixtures.NOW;
import static com.caiodassumpcao.orderflow.orders.domain.OrderFixtures.USD;
import static com.caiodassumpcao.orderflow.orders.domain.OrderFixtures.brl;
import static com.caiodassumpcao.orderflow.orders.domain.OrderFixtures.line;
import static com.caiodassumpcao.orderflow.orders.domain.OrderFixtures.newOrderId;
import static com.caiodassumpcao.orderflow.orders.domain.OrderFixtures.placedOrder;
import static com.caiodassumpcao.orderflow.orders.domain.OrderFixtures.reload;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.caiodassumpcao.orderflow.orders.domain.OrderEvent.OrderCancelled;
import com.caiodassumpcao.orderflow.orders.domain.OrderEvent.OrderConfirmed;
import com.caiodassumpcao.orderflow.orders.domain.OrderEvent.OrderCreated;
import com.caiodassumpcao.orderflow.orders.domain.OrderEvent.OrderShipped;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OrderTest {

  private static final Instant LATER = NOW.plusSeconds(60);

  @Nested
  class Placing {

    @Test
    void startsInCreatedStatusWithTotalOfAllLines() {
      Order order =
          Order.place(
              newOrderId(),
              CUSTOMER,
              List.of(line("SKU-001", 2, "50.00"), line("SKU-002", 1, "150.00")),
              NOW);

      assertThat(order.status()).isEqualTo(OrderStatus.CREATED);
      assertThat(order.total()).isEqualTo(brl("250.00"));
      assertThat(order.currency()).isEqualTo(OrderFixtures.BRL);
      assertThat(order.createdAt()).isEqualTo(NOW);
      assertThat(order.updatedAt()).isEqualTo(NOW);
      assertThat(order.version()).isEmpty();
      assertThat(order.cancellationReason()).isEmpty();
    }

    @Test
    void recordsOrderCreatedWithLinesAndTotal() {
      OrderId id = newOrderId();
      Order order = Order.place(id, CUSTOMER, List.of(line("SKU-001", 3, "10.00")), NOW);

      assertThat(order.pullEvents())
          .containsExactly(
              new OrderCreated(
                  id, 0, CUSTOMER, List.of(line("SKU-001", 3, "10.00")), brl("30.00"), NOW));
    }

    @Test
    void pullingEventsClearsThem() {
      Order order = Order.place(newOrderId(), CUSTOMER, List.of(line("SKU-001", 1, "1.00")), NOW);

      order.pullEvents();

      assertThat(order.pullEvents()).isEmpty();
    }

    @Test
    void rejectsOrderWithoutItems() {
      assertThatThrownBy(() -> Order.place(newOrderId(), CUSTOMER, List.of(), NOW))
          .isInstanceOf(InvalidOrderException.class)
          .hasMessageContaining("at least one item");
      assertThatThrownBy(() -> Order.place(newOrderId(), CUSTOMER, null, NOW))
          .isInstanceOf(InvalidOrderException.class);
    }

    @Test
    void rejectsMoreThanTheMaximumNumberOfItems() {
      List<OrderLine> lines = new ArrayList<>();
      IntStream.rangeClosed(1, Order.MAX_LINES + 1)
          .forEach(i -> lines.add(line("SKU-%03d".formatted(i), 1, "1.00")));

      assertThatThrownBy(() -> Order.place(newOrderId(), CUSTOMER, lines, NOW))
          .isInstanceOf(InvalidOrderException.class)
          .hasMessageContaining("at most " + Order.MAX_LINES);
    }

    @Test
    void rejectsItemsInDifferentCurrencies() {
      OrderLine dollars = new OrderLine(new Sku("SKU-002"), 1, new Money(BigDecimal.ONE, USD));

      assertThatThrownBy(() ->
              Order.place(
                  newOrderId(), CUSTOMER, List.of(line("SKU-001", 1, "1.00"), dollars), NOW))
          .isInstanceOf(InvalidOrderException.class)
          .hasMessageContaining("same currency");
    }

    @Test
    void rejectsDuplicateSkus() {
      assertThatThrownBy(() ->
              Order.place(
                  newOrderId(),
                  CUSTOMER,
                  List.of(line("SKU-001", 1, "1.00"), line("SKU-001", 2, "1.00")),
                  NOW))
          .isInstanceOf(InvalidOrderException.class)
          .hasMessageContaining("Duplicate SKU");
    }

    @Test
    void keepsAnImmutableCopyOfTheLines() {
      List<OrderLine> lines = new ArrayList<>(List.of(line("SKU-001", 1, "1.00")));
      Order order = Order.place(newOrderId(), CUSTOMER, lines, NOW);

      lines.add(line("SKU-002", 1, "1.00"));

      assertThat(order.lines()).hasSize(1);
      assertThatThrownBy(() -> order.lines().add(line("SKU-003", 1, "1.00")))
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Nested
  class Lifecycle {

    @Test
    void followsTheHappyPathUntilShippedWithIncreasingEventVersions() {
      Order order = placedOrder();

      order.markStockReserved(NOW);
      assertThat(order.status()).isEqualTo(OrderStatus.STOCK_RESERVED);
      assertThat(order.pullEvents()).isEmpty();

      order.confirm(NOW);
      assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
      assertThat(order.pullEvents()).containsExactly(new OrderConfirmed(order.id(), 1, NOW));

      Order saved = reload(order, 1);
      saved.ship(LATER);
      assertThat(saved.status()).isEqualTo(OrderStatus.SHIPPED);
      assertThat(saved.updatedAt()).isEqualTo(LATER);
      assertThat(saved.pullEvents()).containsExactly(new OrderShipped(order.id(), 2, LATER));
    }

    @Test
    void refusesASecondChangeBeforeTheFirstIsSaved() {
      Order order = Order.place(newOrderId(), CUSTOMER, List.of(line("SKU-001", 1, "1.00")), NOW);
      order.pullEvents();

      assertThatThrownBy(() -> order.cancel(null, LATER))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("unsaved change");
      assertThat(order.status()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void cannotConfirmBeforeStockIsReserved() {
      Order order = placedOrder();

      assertThatThrownBy(() -> order.confirm(NOW))
          .isInstanceOf(IllegalOrderStateException.class)
          .hasMessage("Cannot confirm an order in status CREATED");
    }

    @Test
    void cannotShipBeforeConfirmation() {
      Order order = placedOrder();

      assertThatThrownBy(() -> order.ship(NOW))
          .isInstanceOfSatisfying(
              IllegalOrderStateException.class,
              e -> {
                assertThat(e.currentStatus()).isEqualTo(OrderStatus.CREATED);
                assertThat(e.action()).isEqualTo("ship");
              });
      assertThat(order.status()).isEqualTo(OrderStatus.CREATED);
      assertThat(order.pullEvents()).isEmpty();
    }

    @Test
    void cannotReserveStockTwice() {
      Order order = placedOrder();
      order.markStockReserved(NOW);

      assertThatThrownBy(() -> order.markStockReserved(NOW))
          .isInstanceOf(IllegalOrderStateException.class);
    }
  }

  @Nested
  class Cancelling {

    @Test
    void cancelsACreatedOrderRecordingThePreviousStatusAndReason() {
      Order order = placedOrder();

      order.cancel("  Changed my mind  ", LATER);

      assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
      assertThat(order.cancellationReason()).contains("Changed my mind");
      assertThat(order.updatedAt()).isEqualTo(LATER);
      assertThat(order.pullEvents())
          .containsExactly(
              new OrderCancelled(order.id(), 1, OrderStatus.CREATED, "Changed my mind", LATER));
    }

    @Test
    void cancelsAnOrderWithReservedStockSoInventoryCanReleaseIt() {
      Order order = placedOrder();
      order.markStockReserved(NOW);

      order.cancel(null, LATER);

      assertThat(order.cancellationReason()).isEmpty();
      assertThat(order.pullEvents())
          .containsExactly(
              new OrderCancelled(order.id(), 1, OrderStatus.STOCK_RESERVED, null, LATER));
    }

    @Test
    void treatsABlankReasonAsNoReason() {
      Order order = placedOrder();

      order.cancel("   ", LATER);

      assertThat(order.cancellationReason()).isEmpty();
    }

    @Test
    void rejectsAReasonLongerThanTheLimit() {
      Order order = placedOrder();
      String reason = "x".repeat(Order.MAX_CANCELLATION_REASON_LENGTH + 1);

      assertThatThrownBy(() -> order.cancel(reason, LATER))
          .isInstanceOf(InvalidOrderException.class);
      assertThat(order.status()).isEqualTo(OrderStatus.CREATED);
    }

    @ParameterizedTest
    @EnumSource(
        value = OrderStatus.class,
        names = {"CONFIRMED", "SHIPPED", "CANCELLED"})
    void cannotCancelOnceConfirmedShippedOrCancelled(OrderStatus status) {
      Order order =
          Order.rehydrate(
              newOrderId(),
              CUSTOMER,
              List.of(line("SKU-001", 1, "1.00")),
              status,
              null,
              NOW,
              NOW,
              3L);

      assertThatThrownBy(() -> order.cancel("too late", LATER))
          .isInstanceOf(IllegalOrderStateException.class)
          .hasMessage("Cannot cancel an order in status " + status);
    }
  }

  @Nested
  class Rehydrating {

    @Test
    void restoresStateWithoutRecordingEvents() {
      OrderId id = newOrderId();
      Order order =
          Order.rehydrate(
              id,
              CUSTOMER,
              List.of(line("SKU-001", 2, "5.00")),
              OrderStatus.CANCELLED,
              "out of stock",
              NOW,
              LATER,
              7L);

      assertThat(order.id()).isEqualTo(id);
      assertThat(order.customerId()).isEqualTo(CUSTOMER);
      assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
      assertThat(order.cancellationReason()).contains("out of stock");
      assertThat(order.total()).isEqualTo(brl("10.00"));
      assertThat(order.createdAt()).isEqualTo(NOW);
      assertThat(order.updatedAt()).isEqualTo(LATER);
      assertThat(order.version()).contains(7L);
      assertThat(order.pullEvents()).isEmpty();
    }
  }

  @Test
  void isOwnedOnlyByItsCustomer() {
    Order order = placedOrder();

    assertThat(order.isOwnedBy(CUSTOMER)).isTrue();
    assertThat(order.isOwnedBy(new CustomerId("someone-else"))).isFalse();
  }
}

package com.caiodassumpcao.orderflow.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StockReservationTest {

  private static final Currency BRL = Currency.getInstance("BRL");
  private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");
  private static final Sku A = new Sku("SKU-A01");
  private static final Sku B = new Sku("SKU-B01");

  private static StockItem item(Sku sku, String price, int available) {
    return new StockItem(sku, BRL, new BigDecimal(price), available, 0, 0);
  }

  private static ReservationLine line(Sku sku, int quantity, String price) {
    return new ReservationLine(sku, quantity, new BigDecimal(price));
  }

  private static Map<Sku, StockItem> stock(StockItem... items) {
    Map<Sku, StockItem> map = new HashMap<>();
    for (StockItem item : items) {
      map.put(item.sku(), item);
    }
    return map;
  }

  @Nested
  class Deciding {

    @Test
    void reservesEveryLineWhenAllAreAvailableAtCatalogPrice() {
      UUID orderId = UUID.randomUUID();
      Map<Sku, StockItem> stock = stock(item(A, "10.00", 5), item(B, "2.50", 3));

      StockReservation reservation =
          StockReservation.decide(
              orderId, BRL, List.of(line(A, 2, "10"), line(B, 3, "2.50")), stock, NOW);

      assertThat(reservation.status()).isEqualTo(ReservationStatus.RESERVED);
      assertThat(stock.get(A).available()).isEqualTo(3);
      assertThat(stock.get(A).reserved()).isEqualTo(2);
      assertThat(stock.get(B).available()).isZero();
      assertThat(reservation.pullEvents())
          .singleElement()
          .isInstanceOfSatisfying(
              InventoryEvent.StockReserved.class,
              e -> {
                assertThat(e.orderId()).isEqualTo(orderId);
                assertThat(e.aggregateVersion()).isZero();
                assertThat(e.lines()).hasSize(2);
              });
    }

    @Test
    void rejectsTheWholeOrderWhenAnyLineLacksStock() {
      Map<Sku, StockItem> stock = stock(item(A, "10.00", 5), item(B, "2.50", 1));

      StockReservation reservation =
          StockReservation.decide(
              UUID.randomUUID(), BRL, List.of(line(A, 2, "10"), line(B, 2, "2.50")), stock, NOW);

      assertThat(reservation.status()).isEqualTo(ReservationStatus.REJECTED);
      assertThat(reservation.rejectionReason()).contains(RejectionReason.OUT_OF_STOCK);
      assertThat(stock.get(A).available()).isEqualTo(5);
      assertThat(stock.get(A).reserved()).isZero();
      assertThat(reservation.pullEvents())
          .singleElement()
          .isInstanceOfSatisfying(
              InventoryEvent.StockRejected.class, e -> assertThat(e.skus()).containsExactly(B));
    }

    @Test
    void sumsRepeatedSkusBeforeCheckingAvailability() {
      Map<Sku, StockItem> stock = stock(item(A, "1.00", 3));

      StockReservation reservation =
          StockReservation.decide(
              UUID.randomUUID(), BRL, List.of(line(A, 2, "1"), line(A, 2, "1")), stock, NOW);

      assertThat(reservation.rejectionReason()).contains(RejectionReason.OUT_OF_STOCK);
      assertThat(stock.get(A).available()).isEqualTo(3);
    }

    @Test
    void rejectsUnknownSkusBeforeAnythingElse() {
      Map<Sku, StockItem> stock = stock(item(A, "10.00", 0));

      StockReservation reservation =
          StockReservation.decide(
              UUID.randomUUID(), BRL, List.of(line(A, 1, "99"), line(B, 1, "1")), stock, NOW);

      assertThat(reservation.rejectionReason()).contains(RejectionReason.UNKNOWN_SKU);
      assertThat(((InventoryEvent.StockRejected) reservation.pullEvents().getFirst()).skus())
          .containsExactly(B);
    }

    @Test
    void rejectsPricesOrCurrenciesThatDifferFromTheCatalog() {
      StockReservation wrongPrice =
          StockReservation.decide(
              UUID.randomUUID(), BRL, List.of(line(A, 1, "9.99")), stock(item(A, "10.00", 5)), NOW);
      StockReservation wrongCurrency =
          StockReservation.decide(
              UUID.randomUUID(),
              Currency.getInstance("USD"),
              List.of(line(A, 1, "10.00")),
              stock(item(A, "10.00", 5)),
              NOW);

      assertThat(wrongPrice.rejectionReason()).contains(RejectionReason.PRICE_MISMATCH);
      assertThat(wrongCurrency.rejectionReason()).contains(RejectionReason.PRICE_MISMATCH);
    }

    @Test
    void needsAtLeastOneLine() {
      assertThatThrownBy(
              () -> StockReservation.decide(UUID.randomUUID(), BRL, List.of(), stock(), NOW))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  class AfterTheDecision {

    private StockReservation reserved(Map<Sku, StockItem> stock) {
      StockReservation reservation =
          StockReservation.decide(UUID.randomUUID(), BRL, List.of(line(A, 2, "10")), stock, NOW);
      reservation.pullEvents();
      return reservation;
    }

    @Test
    void releasingReturnsTheUnitsToTheFreeStock() {
      Map<Sku, StockItem> stock = stock(item(A, "10.00", 5));
      StockReservation reservation = reserved(stock);

      assertThat(reservation.release(stock, NOW.plusSeconds(1))).isTrue();

      assertThat(reservation.status()).isEqualTo(ReservationStatus.RELEASED);
      assertThat(reservation.updatedAt()).isEqualTo(NOW.plusSeconds(1));
      assertThat(stock.get(A).available()).isEqualTo(5);
      assertThat(stock.get(A).reserved()).isZero();
      assertThat(reservation.pullEvents()).isEmpty();
    }

    @Test
    void fulfillingTakesTheReservedUnitsOutOfStock() {
      Map<Sku, StockItem> stock = stock(item(A, "10.00", 5));
      StockReservation reservation = reserved(stock);

      assertThat(reservation.fulfill(stock, NOW)).isTrue();

      assertThat(reservation.status()).isEqualTo(ReservationStatus.FULFILLED);
      assertThat(stock.get(A).available()).isEqualTo(3);
      assertThat(stock.get(A).reserved()).isZero();
    }

    @Test
    void doesNothingOnceTheReservationIsNoLongerActive() {
      Map<Sku, StockItem> stock = stock(item(A, "10.00", 5));
      StockReservation reservation = reserved(stock);
      reservation.release(stock, NOW);

      assertThat(reservation.release(stock, NOW)).isFalse();
      assertThat(reservation.fulfill(stock, NOW)).isFalse();
      assertThat(stock.get(A).available()).isEqualTo(5);
    }

    @Test
    void aCancellationThatArrivedFirstHoldsNoStock() {
      StockReservation tombstone =
          StockReservation.cancelledBeforeReservation(UUID.randomUUID(), NOW);

      assertThat(tombstone.status()).isEqualTo(ReservationStatus.CANCELLED_BEFORE_RESERVATION);
      assertThat(tombstone.lines()).isEmpty();
      assertThat(tombstone.version()).isEmpty();
      assertThat(tombstone.release(stock(), NOW)).isFalse();
    }

    @Test
    void rehydratesWithoutEvents() {
      UUID orderId = UUID.randomUUID();
      StockReservation reservation =
          StockReservation.rehydrate(
              orderId,
              List.of(line(A, 1, "10")),
              ReservationStatus.REJECTED,
              RejectionReason.OUT_OF_STOCK,
              NOW,
              NOW,
              3);

      assertThat(reservation.orderId()).isEqualTo(orderId);
      assertThat(reservation.createdAt()).isEqualTo(NOW);
      assertThat(reservation.version()).contains(3L);
      assertThat(reservation.rejectionReason()).contains(RejectionReason.OUT_OF_STOCK);
      assertThat(reservation.pullEvents()).isEmpty();
    }
  }

  @Nested
  class StockItems {

    @Test
    void refusesInvalidMovements() {
      StockItem item = item(A, "10.00", 1);

      assertThat(item.canReserve(0)).isFalse();
      assertThatThrownBy(() -> item.reserve(2)).isInstanceOf(IllegalStateException.class);
      assertThatThrownBy(() -> item.release(1)).isInstanceOf(IllegalStateException.class);
      assertThatThrownBy(() -> item.fulfill(1)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validatesItsState() {
      assertThatThrownBy(() -> new StockItem(A, BRL, BigDecimal.ZERO, 1, 0, 0))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> new StockItem(A, BRL, BigDecimal.ONE, -1, 0, 0))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> new Sku("bad sku")).isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> line(A, 0, "1")).isInstanceOf(IllegalArgumentException.class);
      assertThat(A.compareTo(B)).isNegative();
      assertThat(A).hasToString("SKU-A01");
    }

    @Test
    void exposesCatalogData() {
      StockItem item = new StockItem(A, BRL, new BigDecimal("10"), 4, 1, 7);

      assertThat(item.unitPrice()).isEqualByComparingTo("10.00");
      assertThat(item.currency()).isEqualTo(BRL);
      assertThat(item.version()).isEqualTo(7);
      assertThat(item.isSoldAt(new BigDecimal("10.00"), BRL)).isTrue();
    }
  }
}

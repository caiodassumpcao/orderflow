package com.caiodassumpcao.orderflow.inventory.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.caiodassumpcao.orderflow.TestcontainersConfiguration;
import com.caiodassumpcao.orderflow.inventory.application.StockReservationService;
import com.caiodassumpcao.orderflow.inventory.domain.ReservationLine;
import com.caiodassumpcao.orderflow.inventory.domain.ReservationStatus;
import com.caiodassumpcao.orderflow.inventory.domain.Sku;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/** Reservas com PostgreSQL real: travas, concorrência e quantidades persistidas. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class StockReservationIT {

  private static final Currency BRL = Currency.getInstance("BRL");

  @Autowired private StockReservationService reservations;
  @Autowired private JdbcClient jdbc;

  private Sku newSku(int available) {
    Sku sku = new Sku("IT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    jdbc.sql(
            "INSERT INTO command.stock_items (sku, currency, unit_price, available)"
                + " VALUES (?, 'BRL', 10.00, ?)")
        .param(sku.value())
        .param(available)
        .update();
    return sku;
  }

  private Map<String, Object> stock(Sku sku) {
    return jdbc.sql("SELECT available, reserved FROM command.stock_items WHERE sku = ?")
        .param(sku.value())
        .query()
        .singleRow();
  }

  private static List<ReservationLine> units(Sku sku, int quantity) {
    return List.of(new ReservationLine(sku, quantity, new BigDecimal("10.00")));
  }

  @Test
  void concurrentOrdersNeverReserveMoreThanTheStock() throws Exception {
    Sku sku = newSku(10);
    int orders = 5;
    CountDownLatch start = new CountDownLatch(1);
    Callable<ReservationStatus> reserveSix =
        () -> {
          start.await();
          return reservations.reserve(UUID.randomUUID(), BRL, units(sku, 6));
        };

    try (ExecutorService pool = Executors.newFixedThreadPool(orders)) {
      List<Future<ReservationStatus>> results =
          IntStream.range(0, orders).mapToObj(i -> pool.submit(reserveSix)).toList();
      start.countDown();

      List<ReservationStatus> statuses = new ArrayList<>();
      for (Future<ReservationStatus> result : results) {
        statuses.add(result.get());
      }
      assertThat(statuses).filteredOn(s -> s == ReservationStatus.RESERVED).hasSize(1);
      assertThat(statuses).filteredOn(s -> s == ReservationStatus.REJECTED).hasSize(orders - 1);
    }
    assertThat(stock(sku)).containsEntry("available", 4).containsEntry("reserved", 6);
  }

  @Test
  void ordersLockingTheSameProductsInOppositeOrderDoNotDeadlock() throws Exception {
    Sku a = newSku(100);
    Sku b = newSku(100);
    int rounds = 20;
    CountDownLatch start = new CountDownLatch(1);

    try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
      List<Future<ReservationStatus>> results = new ArrayList<>();
      for (int i = 0; i < rounds; i++) {
        List<ReservationLine> lines =
            i % 2 == 0
                ? List.of(units(a, 1).getFirst(), units(b, 1).getFirst())
                : List.of(units(b, 1).getFirst(), units(a, 1).getFirst());
        results.add(
            pool.submit(() -> {
              start.await();
              return reservations.reserve(UUID.randomUUID(), BRL, lines);
            }));
      }
      start.countDown();
      for (Future<ReservationStatus> result : results) {
        assertThat(result.get()).isEqualTo(ReservationStatus.RESERVED);
      }
    }
    assertThat(stock(a)).containsEntry("available", 80).containsEntry("reserved", 20);
    assertThat(stock(b)).containsEntry("available", 80).containsEntry("reserved", 20);
  }

  @Test
  void releasingAndFulfillingMoveTheReservedUnits() {
    Sku sku = newSku(10);
    UUID cancelled = UUID.randomUUID();
    UUID shipped = UUID.randomUUID();
    reservations.reserve(cancelled, BRL, units(sku, 3));
    reservations.reserve(shipped, BRL, units(sku, 2));

    assertThat(reservations.release(cancelled)).isEqualTo(ReservationStatus.RELEASED);
    assertThat(reservations.fulfill(shipped)).isEqualTo(ReservationStatus.FULFILLED);

    assertThat(stock(sku)).containsEntry("available", 8).containsEntry("reserved", 0);
    assertThat(
            jdbc.sql("SELECT status FROM command.stock_reservations WHERE order_id IN (?, ?)")
                .param(cancelled)
                .param(shipped)
                .query(String.class)
                .list())
        .containsExactlyInAnyOrder("RELEASED", "FULFILLED");
  }

  @Test
  void aCancellationThatArrivesFirstBlocksTheLaterReservation() {
    Sku sku = newSku(10);
    UUID orderId = UUID.randomUUID();

    reservations.release(orderId);
    ReservationStatus status = reservations.reserve(orderId, BRL, units(sku, 3));

    assertThat(status).isEqualTo(ReservationStatus.CANCELLED_BEFORE_RESERVATION);
    assertThat(stock(sku)).containsEntry("available", 10).containsEntry("reserved", 0);
  }

  @Test
  void writesTheDecisionToTheOutboxOnTheInventoryTopic() {
    Sku sku = newSku(1);
    UUID reserved = UUID.randomUUID();
    UUID rejected = UUID.randomUUID();

    reservations.reserve(reserved, BRL, units(sku, 1));
    reservations.reserve(rejected, BRL, units(sku, 1));

    assertThat(
            jdbc.sql(
                    "SELECT event_type FROM command.outbox"
                        + " WHERE topic = 'inventory.events' AND aggregate_id IN (?, ?)"
                        + " ORDER BY position")
                .param(reserved)
                .param(rejected)
                .query(String.class)
                .list())
        .containsExactly("StockReserved", "StockRejected");
  }
}

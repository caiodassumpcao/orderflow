package com.caiodassumpcao.orderflow.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.caiodassumpcao.orderflow.inventory.application.port.InventoryEventOutbox;
import com.caiodassumpcao.orderflow.inventory.application.port.InventoryUnitOfWork;
import com.caiodassumpcao.orderflow.inventory.application.port.StockReservationRepository;
import com.caiodassumpcao.orderflow.inventory.domain.InventoryEvent;
import com.caiodassumpcao.orderflow.inventory.domain.ReservationLine;
import com.caiodassumpcao.orderflow.inventory.domain.ReservationStatus;
import com.caiodassumpcao.orderflow.inventory.domain.Sku;
import com.caiodassumpcao.orderflow.inventory.domain.StockItem;
import com.caiodassumpcao.orderflow.inventory.domain.StockReservation;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class StockReservationServiceTest {

  private static final Currency BRL = Currency.getInstance("BRL");
  private static final Sku A = new Sku("SKU-A01");

  private final InMemoryStockItemRepository stockItems = new InMemoryStockItemRepository();

  private final Map<UUID, StockReservation> reservationRows = new HashMap<>();
  private final List<InventoryEvent> published = new ArrayList<>();

  private final StockReservationRepository reservations =
      new StockReservationRepository() {
        @Override
        public Optional<StockReservation> lockByOrderId(UUID orderId) {
          return Optional.ofNullable(reservationRows.get(orderId));
        }

        @Override
        public void save(StockReservation reservation) {
          reservationRows.put(
              reservation.orderId(),
              StockReservation.rehydrate(
                  reservation.orderId(),
                  reservation.lines(),
                  reservation.status(),
                  reservation.rejectionReason().orElse(null),
                  reservation.createdAt(),
                  reservation.updatedAt(),
                  reservation.version().map(v -> v + 1).orElse(0L)));
        }
      };

  private final InventoryEventOutbox outbox = published::addAll;
  private final InventoryUnitOfWork unitOfWork =
      new InventoryUnitOfWork() {
        @Override
        public <T> T inTransaction(Supplier<T> work) {
          return work.get();
        }
      };

  private final StockReservationService service =
      new StockReservationService(
          stockItems,
          reservations,
          outbox,
          unitOfWork,
          Clock.fixed(Instant.parse("2026-11-25T12:00:00Z"), ZoneOffset.UTC));

  private void stock(int available) {
    stockItems.put(new StockItem(A, "Product A", BRL, new BigDecimal("10.00"), available, 0, 0));
  }

  private static List<ReservationLine> twoUnits() {
    return List.of(new ReservationLine(A, 2, new BigDecimal("10.00")));
  }

  @Test
  void reservesAndPublishesStockReserved() {
    stock(5);
    UUID orderId = UUID.randomUUID();

    assertThat(service.reserve(orderId, BRL, twoUnits())).isEqualTo(ReservationStatus.RESERVED);

    assertThat(stockItems.get(A).available()).isEqualTo(3);
    assertThat(stockItems.get(A).reserved()).isEqualTo(2);
    assertThat(published).singleElement().isInstanceOf(InventoryEvent.StockReserved.class);
  }

  @Test
  void rejectsWithoutTouchingTheStock() {
    stock(1);

    assertThat(service.reserve(UUID.randomUUID(), BRL, twoUnits()))
        .isEqualTo(ReservationStatus.REJECTED);

    assertThat(stockItems.get(A).available()).isEqualTo(1);
    assertThat(published).singleElement().isInstanceOf(InventoryEvent.StockRejected.class);
  }

  @Test
  void decidesEachOrderOnlyOnce() {
    stock(5);
    UUID orderId = UUID.randomUUID();
    service.reserve(orderId, BRL, twoUnits());

    assertThat(service.reserve(orderId, BRL, twoUnits())).isEqualTo(ReservationStatus.RESERVED);

    assertThat(stockItems.get(A).reserved()).isEqualTo(2);
    assertThat(published).hasSize(1);
  }

  @Test
  void releasesTheStockOfACancelledOrder() {
    stock(5);
    UUID orderId = UUID.randomUUID();
    service.reserve(orderId, BRL, twoUnits());

    assertThat(service.release(orderId)).isEqualTo(ReservationStatus.RELEASED);
    assertThat(service.release(orderId)).isEqualTo(ReservationStatus.RELEASED);

    assertThat(stockItems.get(A).available()).isEqualTo(5);
    assertThat(stockItems.get(A).reserved()).isZero();
  }

  @Test
  void ignoresTheCreationWhenTheCancellationArrivedFirst() {
    stock(5);
    UUID orderId = UUID.randomUUID();

    assertThat(service.release(orderId)).isEqualTo(ReservationStatus.CANCELLED_BEFORE_RESERVATION);
    assertThat(service.reserve(orderId, BRL, twoUnits()))
        .isEqualTo(ReservationStatus.CANCELLED_BEFORE_RESERVATION);

    assertThat(stockItems.get(A).available()).isEqualTo(5);
    assertThat(published).isEmpty();
  }

  @Test
  void cancellingARejectedOrderChangesNothing() {
    stock(1);
    UUID orderId = UUID.randomUUID();
    service.reserve(orderId, BRL, twoUnits());

    assertThat(service.release(orderId)).isEqualTo(ReservationStatus.REJECTED);
    assertThat(stockItems.get(A).available()).isEqualTo(1);
  }

  @Test
  void fulfillsTheStockOfAShippedOrderOnce() {
    stock(5);
    UUID orderId = UUID.randomUUID();
    service.reserve(orderId, BRL, twoUnits());

    assertThat(service.fulfill(orderId)).isEqualTo(ReservationStatus.FULFILLED);
    assertThat(service.fulfill(orderId)).isEqualTo(ReservationStatus.FULFILLED);

    assertThat(stockItems.get(A).available()).isEqualTo(3);
    assertThat(stockItems.get(A).reserved()).isZero();
  }

  @Test
  void refusesToFulfillAnOrderWithoutReservation() {
    assertThatThrownBy(() -> service.fulfill(UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("has no reservation");
  }

  @Test
  void queriesTheStockOfAProduct() {
    stock(5);
    InventoryQueryService queries = new InventoryQueryService(stockItems);

    assertThat(queries.getStock(A).available()).isEqualTo(5);
    assertThatThrownBy(() -> queries.getStock(new Sku("SKU-404")))
        .isInstanceOf(StockItemNotFoundException.class)
        .hasMessage("Stock item SKU-404 not found");
  }
}

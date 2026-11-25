package com.caiodassumpcao.orderflow.inventory.application;

import com.caiodassumpcao.orderflow.inventory.application.port.InventoryEventOutbox;
import com.caiodassumpcao.orderflow.inventory.application.port.InventoryUnitOfWork;
import com.caiodassumpcao.orderflow.inventory.application.port.StockItemRepository;
import com.caiodassumpcao.orderflow.inventory.application.port.StockReservationRepository;
import com.caiodassumpcao.orderflow.inventory.domain.ReservationLine;
import com.caiodassumpcao.orderflow.inventory.domain.ReservationStatus;
import com.caiodassumpcao.orderflow.inventory.domain.Sku;
import com.caiodassumpcao.orderflow.inventory.domain.StockItem;
import com.caiodassumpcao.orderflow.inventory.domain.StockReservation;
import java.time.Clock;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Lado do inventário na saga de pedidos. Cada operação roda numa transação que trava a reserva e os
 * produtos envolvidos, grava as mudanças e os eventos na outbox. Tolerante a eventos repetidos e
 * fora de ordem: uma reserva existente nunca é decidida de novo.
 */
public class StockReservationService {

  private final StockItemRepository stockItems;
  private final StockReservationRepository reservations;
  private final InventoryEventOutbox outbox;
  private final InventoryUnitOfWork unitOfWork;
  private final Clock clock;

  public StockReservationService(
      StockItemRepository stockItems,
      StockReservationRepository reservations,
      InventoryEventOutbox outbox,
      InventoryUnitOfWork unitOfWork,
      Clock clock) {
    this.stockItems = Objects.requireNonNull(stockItems);
    this.reservations = Objects.requireNonNull(reservations);
    this.outbox = Objects.requireNonNull(outbox);
    this.unitOfWork = Objects.requireNonNull(unitOfWork);
    this.clock = Objects.requireNonNull(clock);
  }

  /**
   * Pedido criado: reserva todos os itens ou recusa o pedido.
   *
   * @return a situação da reserva do pedido depois da chamada
   */
  public ReservationStatus reserve(UUID orderId, Currency currency, List<ReservationLine> lines) {
    return unitOfWork.inTransaction(() -> {
      Optional<StockReservation> existing = reservations.lockByOrderId(orderId);
      if (existing.isPresent()) {
        // Repetição, ou o cancelamento chegou antes: a decisão já foi tomada.
        return existing.get().status();
      }
      Map<Sku, StockItem> stock =
          stockItems.lockAll(lines.stream().map(ReservationLine::sku).distinct().toList());
      StockReservation reservation =
          StockReservation.decide(orderId, currency, lines, stock, clock.instant());
      reservations.save(reservation);
      if (reservation.status() == ReservationStatus.RESERVED) {
        stockItems.saveAll(stock.values());
      }
      outbox.append(reservation.pullEvents());
      return reservation.status();
    });
  }

  /**
   * Pedido cancelado: devolve o estoque reservado. Se o cancelamento chegou antes da criação,
   * registra isso para que a criação seja ignorada.
   */
  public ReservationStatus release(UUID orderId) {
    return unitOfWork.inTransaction(() -> {
      Optional<StockReservation> existing = reservations.lockByOrderId(orderId);
      if (existing.isEmpty()) {
        StockReservation tombstone =
            StockReservation.cancelledBeforeReservation(orderId, clock.instant());
        reservations.save(tombstone);
        return tombstone.status();
      }
      StockReservation reservation = existing.get();
      Map<Sku, StockItem> stock = lockLines(reservation);
      if (reservation.release(stock, clock.instant())) {
        stockItems.saveAll(stock.values());
        reservations.save(reservation);
      }
      return reservation.status();
    });
  }

  /**
   * Pedido enviado: dá baixa no estoque reservado.
   *
   * @throws IllegalStateException se o pedido não tem reserva (um pedido só é enviado depois de
   *     reservado; a mensagem vai para a DLQ para investigação)
   */
  public ReservationStatus fulfill(UUID orderId) {
    return unitOfWork.inTransaction(() -> {
      StockReservation reservation =
          reservations
              .lockByOrderId(orderId)
              .orElseThrow(() ->
                  new IllegalStateException("Shipped order " + orderId + " has no reservation"));
      Map<Sku, StockItem> stock = lockLines(reservation);
      if (reservation.fulfill(stock, clock.instant())) {
        stockItems.saveAll(stock.values());
        reservations.save(reservation);
      }
      return reservation.status();
    });
  }

  private Map<Sku, StockItem> lockLines(StockReservation reservation) {
    return stockItems.lockAll(
        reservation.lines().stream().map(ReservationLine::sku).distinct().toList());
  }
}

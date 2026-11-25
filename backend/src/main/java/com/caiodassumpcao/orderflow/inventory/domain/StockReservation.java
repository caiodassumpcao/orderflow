package com.caiodassumpcao.orderflow.inventory.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Reserva de estoque de um pedido: tudo ou nada. Decide a partir dos produtos travados pela
 * transação e registra {@link InventoryEvent.StockReserved} ou {@link
 * InventoryEvent.StockRejected}.
 */
public final class StockReservation {

  private final UUID orderId;
  private final List<ReservationLine> lines;
  private final Instant createdAt;
  private final Long version;
  private ReservationStatus status;
  private RejectionReason rejectionReason;
  private Instant updatedAt;
  private final List<InventoryEvent> pendingEvents = new ArrayList<>();

  private StockReservation(
      UUID orderId,
      List<ReservationLine> lines,
      ReservationStatus status,
      RejectionReason rejectionReason,
      Instant createdAt,
      Instant updatedAt,
      Long version) {
    this.orderId = Objects.requireNonNull(orderId, "orderId");
    this.lines = List.copyOf(lines);
    this.status = Objects.requireNonNull(status, "status");
    this.rejectionReason = rejectionReason;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    this.version = version;
  }

  /**
   * Decide a reserva do pedido.
   *
   * @param stock produtos do pedido já travados para atualização; SKUs ausentes não existem
   */
  public static StockReservation decide(
      UUID orderId,
      Currency currency,
      List<ReservationLine> lines,
      Map<Sku, StockItem> stock,
      Instant now) {
    if (lines == null || lines.isEmpty()) {
      throw new IllegalArgumentException("A reservation needs at least one line");
    }
    Map<Sku, Integer> quantities = new LinkedHashMap<>();
    lines.forEach(line -> quantities.merge(line.sku(), line.quantity(), Integer::sum));

    Optional<StockReservation> rejection =
        reject(orderId, lines, RejectionReason.UNKNOWN_SKU, now, unknown(quantities, stock))
            .or(() ->
                reject(
                    orderId,
                    lines,
                    RejectionReason.PRICE_MISMATCH,
                    now,
                    mispriced(lines, currency, stock)))
            .or(() ->
                reject(
                    orderId,
                    lines,
                    RejectionReason.OUT_OF_STOCK,
                    now,
                    insufficient(quantities, stock)));
    if (rejection.isPresent()) {
      return rejection.get();
    }

    quantities.forEach((sku, quantity) -> stock.get(sku).reserve(quantity));
    StockReservation reservation =
        new StockReservation(orderId, lines, ReservationStatus.RESERVED, null, now, now, null);
    reservation.pendingEvents.add(new InventoryEvent.StockReserved(orderId, 0, lines, now));
    return reservation;
  }

  /**
   * O pedido foi cancelado antes de a criação chegar: nada a reservar, e a criação será ignorada.
   */
  public static StockReservation cancelledBeforeReservation(UUID orderId, Instant now) {
    return new StockReservation(
        orderId, List.of(), ReservationStatus.CANCELLED_BEFORE_RESERVATION, null, now, now, null);
  }

  /** Reconstrói uma reserva persistida, sem eventos. */
  public static StockReservation rehydrate(
      UUID orderId,
      List<ReservationLine> lines,
      ReservationStatus status,
      RejectionReason rejectionReason,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    return new StockReservation(
        orderId, lines, status, rejectionReason, createdAt, updatedAt, version);
  }

  /**
   * Devolve o estoque reservado (pedido cancelado). Só age sobre reservas ativas.
   *
   * @return {@code true} se havia estoque reservado para devolver
   */
  public boolean release(Map<Sku, StockItem> stock, Instant now) {
    if (status != ReservationStatus.RESERVED) {
      return false;
    }
    lines.forEach(line -> stock.get(line.sku()).release(line.quantity()));
    status = ReservationStatus.RELEASED;
    updatedAt = now;
    return true;
  }

  /**
   * Dá baixa no estoque reservado (pedido enviado). Só age sobre reservas ativas.
   *
   * @return {@code true} se havia estoque reservado para baixar
   */
  public boolean fulfill(Map<Sku, StockItem> stock, Instant now) {
    if (status != ReservationStatus.RESERVED) {
      return false;
    }
    lines.forEach(line -> stock.get(line.sku()).fulfill(line.quantity()));
    status = ReservationStatus.FULFILLED;
    updatedAt = now;
    return true;
  }

  public List<InventoryEvent> pullEvents() {
    List<InventoryEvent> events = List.copyOf(pendingEvents);
    pendingEvents.clear();
    return events;
  }

  public UUID orderId() {
    return orderId;
  }

  public List<ReservationLine> lines() {
    return lines;
  }

  public ReservationStatus status() {
    return status;
  }

  public Optional<RejectionReason> rejectionReason() {
    return Optional.ofNullable(rejectionReason);
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public Optional<Long> version() {
    return Optional.ofNullable(version);
  }

  private static Optional<StockReservation> reject(
      UUID orderId,
      List<ReservationLine> lines,
      RejectionReason reason,
      Instant now,
      List<Sku> offending) {
    if (offending.isEmpty()) {
      return Optional.empty();
    }
    StockReservation rejected =
        new StockReservation(orderId, lines, ReservationStatus.REJECTED, reason, now, now, null);
    rejected.pendingEvents.add(
        new InventoryEvent.StockRejected(orderId, 0, reason, offending, now));
    return Optional.of(rejected);
  }

  private static List<Sku> unknown(Map<Sku, Integer> quantities, Map<Sku, StockItem> stock) {
    return quantities.keySet().stream().filter(sku -> !stock.containsKey(sku)).toList();
  }

  private static List<Sku> mispriced(
      List<ReservationLine> lines, Currency currency, Map<Sku, StockItem> stock) {
    return lines.stream()
        .filter(line -> !stock.get(line.sku()).isSoldAt(line.unitPrice(), currency))
        .map(ReservationLine::sku)
        .distinct()
        .toList();
  }

  private static List<Sku> insufficient(Map<Sku, Integer> quantities, Map<Sku, StockItem> stock) {
    return quantities.entrySet().stream()
        .filter(entry -> !stock.get(entry.getKey()).canReserve(entry.getValue()))
        .map(Map.Entry::getKey)
        .toList();
  }
}

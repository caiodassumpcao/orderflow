package com.caiodassumpcao.orderflow.orders.domain;

import com.caiodassumpcao.orderflow.orders.domain.OrderEvent.OrderCancelled;
import com.caiodassumpcao.orderflow.orders.domain.OrderEvent.OrderConfirmed;
import com.caiodassumpcao.orderflow.orders.domain.OrderEvent.OrderCreated;
import com.caiodassumpcao.orderflow.orders.domain.OrderEvent.OrderShipped;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Agregado raiz de pedidos. Garante as invariantes (itens válidos, moeda única, SKUs distintos) e a
 * máquina de estados; cada mudança relevante registra um {@link OrderEvent} que a aplicação grava
 * na outbox na mesma transação.
 */
public final class Order {

  public static final int MAX_LINES = 50;
  public static final int MAX_CANCELLATION_REASON_LENGTH = 200;

  private final OrderId id;
  private final CustomerId customerId;
  private final List<OrderLine> lines;
  private final Money total;
  private final Instant createdAt;
  private final Long version;

  /** Versão do agregado depois da próxima mudança; vai em cada evento registrado. */
  private final long eventVersion;

  private boolean changed;
  private OrderStatus status;
  private String cancellationReason;
  private Instant updatedAt;
  private final List<OrderEvent> pendingEvents = new ArrayList<>();

  private Order(
      OrderId id,
      CustomerId customerId,
      List<OrderLine> lines,
      OrderStatus status,
      String cancellationReason,
      Instant createdAt,
      Instant updatedAt,
      Long version) {
    this.id = Objects.requireNonNull(id, "id is required");
    this.customerId = Objects.requireNonNull(customerId, "customer id is required");
    this.lines = validateLines(lines);
    this.total = sum(this.lines);
    this.status = Objects.requireNonNull(status, "status is required");
    this.cancellationReason = cancellationReason;
    this.createdAt = Objects.requireNonNull(createdAt, "created at is required");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updated at is required");
    this.version = version;
    this.eventVersion = version == null ? 0 : version + 1;
  }

  /** Cria um pedido novo no estado {@code CREATED} e registra {@link OrderCreated}. */
  public static Order place(OrderId id, CustomerId customerId, List<OrderLine> lines, Instant now) {
    Order order = new Order(id, customerId, lines, OrderStatus.CREATED, null, now, now, null);
    order.record(new OrderCreated(id, 0, customerId, order.lines, order.total, now));
    return order;
  }

  /** Reconstrói um pedido já persistido, sem registrar eventos. */
  public static Order rehydrate(
      OrderId id,
      CustomerId customerId,
      List<OrderLine> lines,
      OrderStatus status,
      String cancellationReason,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    return new Order(
        id, customerId, lines, status, cancellationReason, createdAt, updatedAt, version);
  }

  /** Estoque reservado pelo contexto de inventário (saga). Não emite evento próprio. */
  public void markStockReserved(Instant now) {
    requireStatus(OrderStatus.CREATED, "reserve stock for");
    transitionTo(OrderStatus.STOCK_RESERVED, now);
  }

  public void confirm(Instant now) {
    requireStatus(OrderStatus.STOCK_RESERVED, "confirm");
    requireUnchanged();
    transitionTo(OrderStatus.CONFIRMED, now);
    record(new OrderConfirmed(id, eventVersion, now));
  }

  public void ship(Instant now) {
    requireStatus(OrderStatus.CONFIRMED, "ship");
    requireUnchanged();
    transitionTo(OrderStatus.SHIPPED, now);
    record(new OrderShipped(id, eventVersion, now));
  }

  /** Cancela o pedido; permitido apenas antes da confirmação. */
  public void cancel(String reason, Instant now) {
    if (!status.isCancellable()) {
      throw new IllegalOrderStateException(status, "cancel");
    }
    requireUnchanged();
    String normalized = normalizeReason(reason);
    OrderStatus previous = status;
    cancellationReason = normalized;
    transitionTo(OrderStatus.CANCELLED, now);
    record(new OrderCancelled(id, eventVersion, previous, normalized, now));
  }

  public boolean isOwnedBy(CustomerId candidate) {
    return customerId.equals(candidate);
  }

  /** Devolve e limpa os eventos registrados desde a última chamada. */
  public List<OrderEvent> pullEvents() {
    List<OrderEvent> events = List.copyOf(pendingEvents);
    pendingEvents.clear();
    return events;
  }

  public OrderId id() {
    return id;
  }

  public CustomerId customerId() {
    return customerId;
  }

  public List<OrderLine> lines() {
    return lines;
  }

  public Money total() {
    return total;
  }

  public Currency currency() {
    return total.currency();
  }

  public OrderStatus status() {
    return status;
  }

  public Optional<String> cancellationReason() {
    return Optional.ofNullable(cancellationReason);
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  /** Versão de persistência (controle otimista); vazia para pedidos ainda não salvos. */
  public Optional<Long> version() {
    return Optional.ofNullable(version);
  }

  /**
   * Cada evento leva a versão que o agregado terá depois de salvo. Duas mudanças com evento na
   * mesma instância antes de salvar produziriam versões inconsistentes com a persistida; por isso
   * uma instância carregada aceita uma única mudança com evento.
   */
  private void requireUnchanged() {
    if (changed) {
      throw new IllegalStateException(
          "Order " + id + " already has an unsaved change; save and reload it first");
    }
  }

  private void record(OrderEvent event) {
    changed = true;
    pendingEvents.add(event);
  }

  private void requireStatus(OrderStatus expected, String action) {
    if (status != expected) {
      throw new IllegalOrderStateException(status, action);
    }
  }

  private void transitionTo(OrderStatus next, Instant now) {
    status = next;
    updatedAt = Objects.requireNonNull(now, "now is required");
  }

  private static List<OrderLine> validateLines(List<OrderLine> lines) {
    if (lines == null || lines.isEmpty()) {
      throw new InvalidOrderException("An order must have at least one item");
    }
    if (lines.size() > MAX_LINES) {
      throw new InvalidOrderException("An order must have at most " + MAX_LINES + " items");
    }
    Currency currency = lines.getFirst().unitPrice().currency();
    Set<Sku> skus = new HashSet<>();
    for (OrderLine line : lines) {
      Objects.requireNonNull(line, "order line is required");
      if (!line.unitPrice().currency().equals(currency)) {
        throw new InvalidOrderException("All items must use the same currency");
      }
      if (!skus.add(line.sku())) {
        throw new InvalidOrderException("Duplicate SKU in order: " + line.sku());
      }
    }
    return List.copyOf(lines);
  }

  private static Money sum(List<OrderLine> lines) {
    Money result = Money.zero(lines.getFirst().unitPrice().currency());
    for (OrderLine line : lines) {
      result = result.plus(line.total());
    }
    return result;
  }

  private static String normalizeReason(String reason) {
    if (reason == null || reason.isBlank()) {
      return null;
    }
    String trimmed = reason.strip();
    if (trimmed.length() > MAX_CANCELLATION_REASON_LENGTH) {
      throw new InvalidOrderException(
          "Cancellation reason must have at most "
              + MAX_CANCELLATION_REASON_LENGTH
              + " characters");
    }
    return trimmed;
  }
}

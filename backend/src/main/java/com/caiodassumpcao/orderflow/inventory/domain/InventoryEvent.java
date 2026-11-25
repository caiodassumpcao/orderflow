package com.caiodassumpcao.orderflow.inventory.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Eventos do inventário, publicados em {@code inventory.events}. */
public sealed interface InventoryEvent {

  /** Pedido a que a reserva pertence; também é o id do agregado no envelope. */
  UUID orderId();

  long aggregateVersion();

  Instant occurredAt();

  /** Todo o estoque do pedido foi reservado. */
  record StockReserved(
      UUID orderId, long aggregateVersion, List<ReservationLine> lines, Instant occurredAt)
      implements InventoryEvent {

    public StockReserved {
      Objects.requireNonNull(orderId);
      Objects.requireNonNull(occurredAt);
      lines = List.copyOf(lines);
    }
  }

  /** O pedido foi recusado; nada foi reservado. */
  record StockRejected(
      UUID orderId,
      long aggregateVersion,
      RejectionReason reason,
      List<Sku> skus,
      Instant occurredAt)
      implements InventoryEvent {

    public StockRejected {
      Objects.requireNonNull(orderId);
      Objects.requireNonNull(reason);
      Objects.requireNonNull(occurredAt);
      skus = List.copyOf(skus);
    }
  }
}

package com.caiodassumpcao.orderflow.orders.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Eventos de domínio emitidos pelo agregado {@link Order}. O envelope de publicação (eventId,
 * versão, correlationId) é responsabilidade dos adapters de mensageria.
 */
public sealed interface OrderEvent {

  OrderId orderId();

  Instant occurredAt();

  /** Pedido criado e aguardando reserva de estoque. */
  record OrderCreated(
      OrderId orderId,
      CustomerId customerId,
      List<OrderLine> lines,
      Money total,
      Instant occurredAt)
      implements OrderEvent {

    public OrderCreated {
      Objects.requireNonNull(orderId);
      Objects.requireNonNull(customerId);
      Objects.requireNonNull(total);
      Objects.requireNonNull(occurredAt);
      lines = List.copyOf(lines);
    }
  }

  /** Pedido confirmado depois da reserva de estoque. */
  record OrderConfirmed(OrderId orderId, Instant occurredAt) implements OrderEvent {

    public OrderConfirmed {
      Objects.requireNonNull(orderId);
      Objects.requireNonNull(occurredAt);
    }
  }

  /** Pedido enviado. */
  record OrderShipped(OrderId orderId, Instant occurredAt) implements OrderEvent {

    public OrderShipped {
      Objects.requireNonNull(orderId);
      Objects.requireNonNull(occurredAt);
    }
  }

  /**
   * Pedido cancelado. {@code previousStatus} permite ao estoque saber se havia reserva a liberar.
   */
  record OrderCancelled(
      OrderId orderId, OrderStatus previousStatus, String reason, Instant occurredAt)
      implements OrderEvent {

    public OrderCancelled {
      Objects.requireNonNull(orderId);
      Objects.requireNonNull(previousStatus);
      Objects.requireNonNull(occurredAt);
    }
  }
}

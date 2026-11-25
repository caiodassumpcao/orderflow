package com.caiodassumpcao.orderflow.orders.application.projection;

import com.caiodassumpcao.orderflow.orders.domain.CustomerId;
import com.caiodassumpcao.orderflow.orders.domain.OrderId;
import com.caiodassumpcao.orderflow.orders.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Objects;

/** Fatos que alteram a projeção de um pedido, já traduzidos dos contratos de evento. */
public sealed interface ProjectionEvent {

  OrderId orderId();

  /** Versão do pedido depois do evento; a projeção ignora versões que já superou. */
  long aggregateVersion();

  Instant occurredAt();

  /** Pedido criado: traz os detalhes que só esse evento tem. */
  record OrderCreated(
      OrderId orderId,
      long aggregateVersion,
      Instant occurredAt,
      CustomerId customerId,
      Currency currency,
      BigDecimal total,
      List<Item> items)
      implements ProjectionEvent {

    public OrderCreated {
      Objects.requireNonNull(orderId);
      Objects.requireNonNull(occurredAt);
      Objects.requireNonNull(customerId);
      Objects.requireNonNull(currency);
      Objects.requireNonNull(total);
      items = List.copyOf(items);
    }
  }

  /** Mudança de status (confirmado, enviado, cancelado). */
  record StatusChanged(
      OrderId orderId,
      long aggregateVersion,
      Instant occurredAt,
      OrderStatus status,
      String cancellationReason)
      implements ProjectionEvent {

    public StatusChanged {
      Objects.requireNonNull(orderId);
      Objects.requireNonNull(occurredAt);
      Objects.requireNonNull(status);
    }
  }

  /** Item do pedido. */
  record Item(String sku, int quantity, BigDecimal unitPrice) {}
}

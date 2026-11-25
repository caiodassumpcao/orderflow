package com.caiodassumpcao.orderflow.orders.adapter.messaging;

import com.caiodassumpcao.orderflow.orders.application.port.OrderEventOutbox;
import com.caiodassumpcao.orderflow.orders.domain.OrderEvent;
import com.caiodassumpcao.orderflow.orders.domain.OrderEvent.OrderCancelled;
import com.caiodassumpcao.orderflow.orders.domain.OrderEvent.OrderConfirmed;
import com.caiodassumpcao.orderflow.orders.domain.OrderEvent.OrderCreated;
import com.caiodassumpcao.orderflow.orders.domain.OrderEvent.OrderShipped;
import com.caiodassumpcao.orderflow.orders.domain.OrderLine;
import com.caiodassumpcao.orderflow.shared.id.UuidV7;
import com.caiodassumpcao.orderflow.shared.messaging.OutboxMessage;
import com.caiodassumpcao.orderflow.shared.messaging.OutboxWriter;
import com.caiodassumpcao.orderflow.shared.observability.CorrelationIdFilter;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Converte os eventos de domínio de pedidos nos contratos públicos (versão 1, ver {@code
 * docs/events/order-events.md}) e os grava na outbox.
 */
@Component
class OutboxOrderEventPublisher implements OrderEventOutbox {

  static final String AGGREGATE_TYPE = "Order";
  static final String TOPIC = OrderTopics.ORDER_EVENTS;
  static final int CONTRACT_VERSION = 1;

  private final OutboxWriter writer;
  private final JsonMapper json;

  OutboxOrderEventPublisher(OutboxWriter writer, JsonMapper json) {
    this.writer = writer;
    this.json = json;
  }

  @Override
  public void append(List<OrderEvent> events) {
    String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
    for (OrderEvent event : events) {
      writer.append(
          new OutboxMessage(
              UuidV7.generate(),
              TOPIC,
              AGGREGATE_TYPE,
              event.orderId().value(),
              event.aggregateVersion(),
              event.getClass().getSimpleName(),
              CONTRACT_VERSION,
              json.writeValueAsString(payload(event)),
              correlationId,
              event.occurredAt()));
    }
  }

  static Object payload(OrderEvent event) {
    return switch (event) {
      case OrderCreated created ->
          new OrderCreatedV1(
              created.orderId().toString(),
              created.customerId().value(),
              created.total().currency().getCurrencyCode(),
              created.total().amount().toPlainString(),
              created.lines().stream().map(OrderCreatedV1.Item::of).toList());
      case OrderConfirmed confirmed -> new OrderRefV1(confirmed.orderId().toString());
      case OrderShipped shipped -> new OrderRefV1(shipped.orderId().toString());
      case OrderCancelled cancelled ->
          new OrderCancelledV1(
              cancelled.orderId().toString(),
              cancelled.previousStatus().name(),
              cancelled.reason());
    };
  }

  /** Payload de {@code OrderCreated} v1. Valores monetários como string decimal. */
  record OrderCreatedV1(
      String orderId, String customerId, String currency, String total, List<Item> items) {

    record Item(String sku, int quantity, String unitPrice) {

      static Item of(OrderLine line) {
        return new Item(
            line.sku().value(), line.quantity(), line.unitPrice().amount().toPlainString());
      }
    }
  }

  /** Payload de {@code OrderConfirmed} e {@code OrderShipped} v1. */
  record OrderRefV1(String orderId) {}

  /** Payload de {@code OrderCancelled} v1. */
  record OrderCancelledV1(String orderId, String previousStatus, String reason) {}
}

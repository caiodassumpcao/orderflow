package com.caiodassumpcao.orderflow.inventory.adapter.messaging;

import com.caiodassumpcao.orderflow.inventory.application.port.InventoryEventOutbox;
import com.caiodassumpcao.orderflow.inventory.domain.InventoryEvent;
import com.caiodassumpcao.orderflow.inventory.domain.InventoryEvent.StockRejected;
import com.caiodassumpcao.orderflow.inventory.domain.InventoryEvent.StockReserved;
import com.caiodassumpcao.orderflow.inventory.domain.Sku;
import com.caiodassumpcao.orderflow.shared.id.UuidV7;
import com.caiodassumpcao.orderflow.shared.messaging.OutboxMessage;
import com.caiodassumpcao.orderflow.shared.messaging.OutboxWriter;
import com.caiodassumpcao.orderflow.shared.observability.CorrelationIdFilter;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Converte os eventos do inventário nos contratos v1 (ver {@code docs/events/inventory-events.md})
 * e os grava na outbox.
 */
@Component
class OutboxInventoryEventPublisher implements InventoryEventOutbox {

  static final String AGGREGATE_TYPE = "StockReservation";
  static final int CONTRACT_VERSION = 1;

  private final OutboxWriter writer;
  private final JsonMapper json;

  OutboxInventoryEventPublisher(OutboxWriter writer, JsonMapper json) {
    this.writer = writer;
    this.json = json;
  }

  @Override
  public void append(List<InventoryEvent> events) {
    // Dentro de um consumer, o MDC traz o correlationId do evento de pedido que originou este.
    String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
    for (InventoryEvent event : events) {
      writer.append(
          new OutboxMessage(
              UuidV7.generate(),
              InventoryTopics.INVENTORY_EVENTS,
              AGGREGATE_TYPE,
              event.orderId(),
              event.aggregateVersion(),
              event.getClass().getSimpleName(),
              CONTRACT_VERSION,
              json.writeValueAsString(payload(event)),
              correlationId,
              event.occurredAt()));
    }
  }

  static Object payload(InventoryEvent event) {
    return switch (event) {
      case StockReserved reserved ->
          new StockReservedV1(
              reserved.orderId().toString(),
              reserved.lines().stream()
                  .map(line -> new StockReservedV1.Item(line.sku().value(), line.quantity()))
                  .toList());
      case StockRejected rejected ->
          new StockRejectedV1(
              rejected.orderId().toString(),
              rejected.reason().name(),
              rejected.skus().stream().map(Sku::value).toList());
    };
  }

  /** Payload de {@code StockReserved} v1. */
  record StockReservedV1(String orderId, List<Item> items) {

    record Item(String sku, int quantity) {}
  }

  /** Payload de {@code StockRejected} v1. */
  record StockRejectedV1(String orderId, String reason, List<String> skus) {}
}

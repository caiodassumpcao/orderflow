package com.caiodassumpcao.orderflow.orders.adapter.messaging;

import com.caiodassumpcao.orderflow.orders.application.OrderSagaService;
import com.caiodassumpcao.orderflow.orders.domain.OrderId;
import com.caiodassumpcao.orderflow.shared.messaging.DeadLetters;
import com.caiodassumpcao.orderflow.shared.messaging.EventEnvelope;
import com.caiodassumpcao.orderflow.shared.messaging.IdempotentEventProcessor;
import com.caiodassumpcao.orderflow.shared.messaging.OrderflowRetryableTopic;
import com.caiodassumpcao.orderflow.shared.messaging.PoisonMessageException;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Pedidos na saga: consome {@code inventory.events} (contrato em {@code
 * docs/events/inventory-events.md}) e confirma ou cancela o pedido.
 */
@Component
class InventoryEventsListener {

  static final String NAME = "order-saga";

  private static final Logger LOG = LoggerFactory.getLogger(InventoryEventsListener.class);
  private static final int SUPPORTED_VERSION = 1;

  private final IdempotentEventProcessor processor;
  private final OrderSagaService saga;
  private final DeadLetters deadLetters;

  InventoryEventsListener(
      IdempotentEventProcessor processor, OrderSagaService saga, DeadLetters deadLetters) {
    this.processor = processor;
    this.saga = saga;
    this.deadLetters = deadLetters;
  }

  @OrderflowRetryableTopic(
      retryTopicSuffix = ".order-saga.retry",
      dltTopicSuffix = ".order-saga.dlq")
  @KafkaListener(
      topics = OrderTopics.INVENTORY_EVENTS,
      groupId = NAME,
      autoStartup = "${orderflow.messaging.consumers.order-saga.enabled:true}")
  void onMessage(ConsumerRecord<String, String> record) {
    processor.process(NAME, record.value(), this::handle);
  }

  @DltHandler
  void onDeadLetter(
      ConsumerRecord<String, String> record,
      @Header(name = KafkaHeaders.EXCEPTION_MESSAGE, required = false) byte[] reason) {
    deadLetters.record(NAME, record.topic(), record.key(), reason);
  }

  void handle(EventEnvelope envelope) {
    switch (envelope.type()) {
      case "StockReserved" -> saga.onStockReserved(orderId(supported(envelope)));
      case "StockRejected" ->
          saga.onStockRejected(orderId(supported(envelope)), rejectionReason(envelope.payload()));
      default -> LOG.debug("Order saga ignores {} ({})", envelope.type(), envelope.eventId());
    }
  }

  /**
   * Motivo legível gravado no pedido cancelado, ex.: {@code Stock rejected: OUT_OF_STOCK (SKU-1)}.
   */
  static String rejectionReason(JsonNode payload) {
    JsonNode reason = payload.get("reason");
    if (reason == null || reason.isNull()) {
      throw new PoisonMessageException("StockRejected without reason");
    }
    List<String> skus = new ArrayList<>();
    JsonNode skuNodes = payload.get("skus");
    if (skuNodes != null && skuNodes.isArray()) {
      skuNodes.forEach(sku -> skus.add(sku.asString()));
    }
    return "Stock rejected: "
        + reason.asString()
        + (skus.isEmpty() ? "" : " (" + String.join(", ", skus) + ")");
  }

  private static OrderId orderId(EventEnvelope envelope) {
    return new OrderId(envelope.aggregateId());
  }

  private static EventEnvelope supported(EventEnvelope envelope) {
    if (envelope.version() != SUPPORTED_VERSION) {
      throw new PoisonMessageException(
          "Unsupported " + envelope.type() + " contract version " + envelope.version());
    }
    return envelope;
  }
}

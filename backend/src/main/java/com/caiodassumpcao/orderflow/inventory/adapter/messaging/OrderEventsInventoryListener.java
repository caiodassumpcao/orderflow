package com.caiodassumpcao.orderflow.inventory.adapter.messaging;

import com.caiodassumpcao.orderflow.inventory.application.StockReservationService;
import com.caiodassumpcao.orderflow.inventory.domain.ReservationLine;
import com.caiodassumpcao.orderflow.inventory.domain.Sku;
import com.caiodassumpcao.orderflow.shared.messaging.DeadLetters;
import com.caiodassumpcao.orderflow.shared.messaging.EventEnvelope;
import com.caiodassumpcao.orderflow.shared.messaging.IdempotentEventProcessor;
import com.caiodassumpcao.orderflow.shared.messaging.OrderflowRetryableTopic;
import com.caiodassumpcao.orderflow.shared.messaging.PoisonMessageException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
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
 * Inventário na saga: reage a {@code OrderCreated} (reserva ou recusa), {@code OrderCancelled}
 * (devolve o estoque) e {@code OrderShipped} (baixa o estoque reservado). Lê só o contrato dos
 * eventos de pedidos, nunca classes do contexto de pedidos.
 */
@Component
class OrderEventsInventoryListener {

  static final String NAME = "inventory";

  private static final Logger LOG = LoggerFactory.getLogger(OrderEventsInventoryListener.class);
  private static final int SUPPORTED_VERSION = 1;

  private final IdempotentEventProcessor processor;
  private final StockReservationService reservations;
  private final DeadLetters deadLetters;

  OrderEventsInventoryListener(
      IdempotentEventProcessor processor,
      StockReservationService reservations,
      DeadLetters deadLetters) {
    this.processor = processor;
    this.reservations = reservations;
    this.deadLetters = deadLetters;
  }

  @OrderflowRetryableTopic(retryTopicSuffix = ".inventory.retry", dltTopicSuffix = ".inventory.dlq")
  @KafkaListener(
      topics = InventoryTopics.ORDER_EVENTS,
      groupId = NAME,
      autoStartup = "${orderflow.messaging.consumers.inventory.enabled:true}")
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
      case "OrderCreated" -> reserve(supported(envelope));
      case "OrderCancelled" -> reservations.release(supported(envelope).aggregateId());
      case "OrderShipped" -> reservations.fulfill(supported(envelope).aggregateId());
      default -> LOG.debug("Inventory ignores {} ({})", envelope.type(), envelope.eventId());
    }
  }

  private void reserve(EventEnvelope envelope) {
    JsonNode payload = envelope.payload();
    try {
      Currency currency = Currency.getInstance(text(payload, "currency"));
      JsonNode items = required(payload, "items");
      if (!items.isArray() || items.isEmpty()) {
        throw new PoisonMessageException("OrderCreated without items");
      }
      List<ReservationLine> lines = new ArrayList<>();
      for (JsonNode item : items) {
        lines.add(
            new ReservationLine(
                new Sku(text(item, "sku")),
                required(item, "quantity").asInt(),
                new BigDecimal(text(item, "unitPrice"))));
      }
      reservations.reserve(envelope.aggregateId(), currency, lines);
    } catch (IllegalArgumentException e) {
      // Inclui NumberFormatException: payload fora do contrato não vai melhorar com retry.
      throw new PoisonMessageException("Invalid OrderCreated payload", e);
    }
  }

  private static EventEnvelope supported(EventEnvelope envelope) {
    if (envelope.version() != SUPPORTED_VERSION) {
      throw new PoisonMessageException(
          "Unsupported " + envelope.type() + " contract version " + envelope.version());
    }
    return envelope;
  }

  private static JsonNode required(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) {
      throw new PoisonMessageException("Event payload without " + field);
    }
    return value;
  }

  private static String text(JsonNode node, String field) {
    return required(node, field).asString();
  }
}

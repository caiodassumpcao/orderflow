package com.caiodassumpcao.orderflow.orders.adapter.messaging;

import com.caiodassumpcao.orderflow.orders.application.projection.OrderProjectionService;
import com.caiodassumpcao.orderflow.orders.application.projection.ProjectionEvent;
import com.caiodassumpcao.orderflow.orders.domain.CustomerId;
import com.caiodassumpcao.orderflow.orders.domain.OrderId;
import com.caiodassumpcao.orderflow.orders.domain.OrderStatus;
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
 * Projector: consome {@code order.events} e mantém o modelo de leitura. Traduz o contrato v1 dos
 * eventos (ver {@code docs/events/order-events.md}) em {@link ProjectionEvent}.
 */
@Component
class OrderProjectionListener {

  static final String NAME = "order-projector";

  private static final Logger LOG = LoggerFactory.getLogger(OrderProjectionListener.class);
  private static final int SUPPORTED_VERSION = 1;

  private final IdempotentEventProcessor processor;
  private final OrderProjectionService projection;
  private final DeadLetters deadLetters;

  OrderProjectionListener(
      IdempotentEventProcessor processor,
      OrderProjectionService projection,
      DeadLetters deadLetters) {
    this.processor = processor;
    this.projection = projection;
    this.deadLetters = deadLetters;
  }

  @OrderflowRetryableTopic(
      retryTopicSuffix = ".order-projector.retry",
      dltTopicSuffix = ".order-projector.dlq")
  @KafkaListener(
      topics = OrderTopics.ORDER_EVENTS,
      groupId = NAME,
      autoStartup = "${orderflow.messaging.consumers.order-projector.enabled:true}")
  void onMessage(ConsumerRecord<String, String> record) {
    processor.process(NAME, record.value(), this::project);
  }

  @DltHandler
  void onDeadLetter(
      ConsumerRecord<String, String> record,
      @Header(name = KafkaHeaders.EXCEPTION_MESSAGE, required = false) byte[] reason) {
    deadLetters.record(NAME, record.topic(), record.key(), reason);
  }

  void project(EventEnvelope envelope) {
    switch (envelope.type()) {
      case "OrderCreated" -> projection.apply(created(supported(envelope)));
      case "OrderConfirmed" -> projection.apply(status(supported(envelope), OrderStatus.CONFIRMED));
      case "OrderShipped" -> projection.apply(status(supported(envelope), OrderStatus.SHIPPED));
      case "OrderCancelled" -> projection.apply(status(supported(envelope), OrderStatus.CANCELLED));
      // Tipos novos não quebram o projector: ele ignora o que não conhece.
      default -> LOG.debug("Ignoring event type {} ({})", envelope.type(), envelope.eventId());
    }
  }

  private static EventEnvelope supported(EventEnvelope envelope) {
    if (envelope.version() != SUPPORTED_VERSION) {
      throw new PoisonMessageException(
          "Unsupported " + envelope.type() + " contract version " + envelope.version());
    }
    return envelope;
  }

  private static ProjectionEvent.OrderCreated created(EventEnvelope envelope) {
    JsonNode payload = envelope.payload();
    List<ProjectionEvent.Item> items = new ArrayList<>();
    JsonNode itemNodes = required(payload, "items");
    if (!itemNodes.isArray() || itemNodes.isEmpty()) {
      throw new PoisonMessageException("OrderCreated without items");
    }
    for (JsonNode item : itemNodes) {
      items.add(
          new ProjectionEvent.Item(
              text(item, "sku"), required(item, "quantity").asInt(), decimal(item, "unitPrice")));
    }
    try {
      return new ProjectionEvent.OrderCreated(
          new OrderId(envelope.aggregateId()),
          envelope.aggregateVersion(),
          envelope.occurredAt(),
          new CustomerId(text(payload, "customerId")),
          Currency.getInstance(text(payload, "currency")),
          decimal(payload, "total"),
          items);
    } catch (IllegalArgumentException e) {
      throw new PoisonMessageException("Invalid OrderCreated payload", e);
    }
  }

  private static ProjectionEvent.StatusChanged status(EventEnvelope envelope, OrderStatus status) {
    JsonNode reason = envelope.payload().get("reason");
    return new ProjectionEvent.StatusChanged(
        new OrderId(envelope.aggregateId()),
        envelope.aggregateVersion(),
        envelope.occurredAt(),
        status,
        status == OrderStatus.CANCELLED && reason != null && !reason.isNull()
            ? reason.asString()
            : null);
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

  private static BigDecimal decimal(JsonNode node, String field) {
    try {
      return new BigDecimal(text(node, field));
    } catch (NumberFormatException e) {
      throw new PoisonMessageException("Event payload field " + field + " is not a decimal", e);
    }
  }
}

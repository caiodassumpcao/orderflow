package com.caiodassumpcao.orderflow.shared.messaging;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Serializa e valida o {@link EventEnvelope} trafegado como JSON no Kafka. */
@Component
public class EventEnvelopeCodec {

  private final JsonMapper json;

  public EventEnvelopeCodec(JsonMapper json) {
    this.json = json;
  }

  public String encode(EventEnvelope envelope) {
    ObjectNode node = json.createObjectNode();
    node.put("eventId", envelope.eventId().toString());
    node.put("type", envelope.type());
    node.put("version", envelope.version());
    node.put("aggregateId", envelope.aggregateId().toString());
    node.put("occurredAt", envelope.occurredAt().toString());
    node.put("correlationId", envelope.correlationId());
    node.set("payload", envelope.payload());
    return json.writeValueAsString(node);
  }

  /**
   * Lê e valida o envelope.
   *
   * @throws PoisonMessageException se a mensagem não for um envelope válido
   */
  public EventEnvelope decode(String message) {
    if (message == null || message.isBlank()) {
      throw new PoisonMessageException("Empty message");
    }
    try {
      JsonNode node = json.readTree(message);
      JsonNode payload = node.get("payload");
      if (payload == null || !payload.isObject()) {
        throw new PoisonMessageException("Envelope without an object payload");
      }
      JsonNode version = required(node, "version");
      if (!version.isInt() || version.asInt() < 1) {
        throw new PoisonMessageException("Envelope version must be a positive integer");
      }
      JsonNode correlationId = node.get("correlationId");
      return new EventEnvelope(
          UUID.fromString(required(node, "eventId").asString()),
          required(node, "type").asString(),
          version.asInt(),
          UUID.fromString(required(node, "aggregateId").asString()),
          Instant.parse(required(node, "occurredAt").asString()),
          correlationId == null || correlationId.isNull() ? null : correlationId.asString(),
          payload);
    } catch (JacksonException | IllegalArgumentException | DateTimeParseException e) {
      throw new PoisonMessageException("Malformed event envelope", e);
    }
  }

  private static JsonNode required(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) {
      throw new PoisonMessageException("Envelope without " + field);
    }
    return value;
  }
}

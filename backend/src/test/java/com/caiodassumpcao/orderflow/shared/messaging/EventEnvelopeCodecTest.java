package com.caiodassumpcao.orderflow.shared.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class EventEnvelopeCodecTest {

  private final JsonMapper json = JsonMapper.builder().build();
  private final EventEnvelopeCodec codec = new EventEnvelopeCodec(json);

  private EventEnvelope envelope(String correlationId) {
    return new EventEnvelope(
        UUID.randomUUID(),
        "OrderCreated",
        1,
        UUID.randomUUID(),
        Instant.parse("2026-11-24T12:00:00.123Z"),
        correlationId,
        json.readTree("{\"orderId\":\"x\",\"total\":\"10.00\"}"));
  }

  @Test
  void roundTripsTheEnvelope() {
    EventEnvelope original = envelope("corr-1");

    assertThat(codec.decode(codec.encode(original))).isEqualTo(original);
  }

  @Test
  void keepsAMissingCorrelationIdAsNull() {
    EventEnvelope original = envelope(null);

    String encoded = codec.encode(original);

    assertThat(json.readTree(encoded).get("correlationId").isNull()).isTrue();
    assertThat(codec.decode(encoded).correlationId()).isNull();
  }

  @Test
  void writesTheDocumentedFieldNames() {
    String encoded = codec.encode(envelope("corr-1"));

    assertThat(json.readTree(encoded).propertyNames())
        .containsExactly(
            "eventId", "type", "version", "aggregateId", "occurredAt", "correlationId", "payload");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        "not json",
        "[]",
        "{\"type\":\"OrderCreated\"}",
        """
        {"eventId":"not-a-uuid","type":"T","version":1,"aggregateId":"%s",
         "occurredAt":"2026-11-24T12:00:00Z","payload":{}}
        """,
        """
        {"eventId":"%s","type":"T","version":0,"aggregateId":"%s",
         "occurredAt":"2026-11-24T12:00:00Z","payload":{}}
        """,
        """
        {"eventId":"%s","type":"T","version":1,"aggregateId":"%s",
         "occurredAt":"yesterday","payload":{}}
        """,
        """
        {"eventId":"%s","type":"T","version":1,"aggregateId":"%s",
         "occurredAt":"2026-11-24T12:00:00Z","payload":"text"}
        """
      })
  void rejectsAnythingThatIsNotAValidEnvelopeAsPoison(String message) {
    String filled =
        message == null
            ? null
            : message
                .replaceFirst("%s", UUID.randomUUID().toString())
                .replaceFirst("%s", UUID.randomUUID().toString());

    assertThatThrownBy(() -> codec.decode(filled)).isInstanceOf(PoisonMessageException.class);
  }
}

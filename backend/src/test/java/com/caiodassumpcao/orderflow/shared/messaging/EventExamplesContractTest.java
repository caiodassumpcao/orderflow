package com.caiodassumpcao.orderflow.shared.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Os exemplos publicados em docs/events/examples respeitam os schemas e o codec os lê. */
class EventExamplesContractTest {

  @ParameterizedTest
  @FieldSource("com.caiodassumpcao.orderflow.shared.messaging.EventContracts#TYPES")
  void exampleMatchesItsSchemaAndDecodes(String type) {
    String example = EventContracts.example(type);

    assertThat(EventContracts.messageViolations(EventContracts.parse(example))).isEmpty();
    EventEnvelope envelope = new EventEnvelopeCodec(JsonMapper.builder().build()).decode(example);
    assertThat(envelope.type()).isEqualTo(type);
    assertThat(envelope.version()).isEqualTo(1);
  }

  @Test
  void codecWritesEnvelopesThatMatchTheContract() {
    EventEnvelopeCodec codec = new EventEnvelopeCodec(JsonMapper.builder().build());
    ObjectNode payload =
        (ObjectNode) EventContracts.parse("{\"orderId\":\"" + UUID.randomUUID() + "\"}");

    String withCorrelation =
        codec.encode(
            new EventEnvelope(
                UUID.randomUUID(),
                "OrderConfirmed",
                1,
                UUID.randomUUID(),
                2,
                Instant.parse("2026-11-25T12:00:00.123456Z"),
                "corr-1",
                payload));
    String withoutCorrelation =
        codec.encode(
            new EventEnvelope(
                UUID.randomUUID(),
                "OrderConfirmed",
                1,
                UUID.randomUUID(),
                2,
                Instant.parse("2026-11-25T12:00:00Z"),
                null,
                payload));

    assertThat(EventContracts.messageViolations(EventContracts.parse(withCorrelation))).isEmpty();
    assertThat(EventContracts.messageViolations(EventContracts.parse(withoutCorrelation)))
        .isEmpty();
  }

  @Test
  void schemasRejectMessagesOutOfContract() {
    String example = EventContracts.example("OrderCreated");

    assertThat(
            EventContracts.messageViolations(
                EventContracts.parse(example.replace("\"139.80\"", "139.8"))))
        .anySatisfy(violation -> assertThat(violation).contains("total"));
    assertThat(
            EventContracts.messageViolations(
                EventContracts.parse(example.replace("\"version\": 1", "\"version\": 0"))))
        .isNotEmpty();
    assertThat(
            EventContracts.messageViolations(
                EventContracts.parse(
                    example.replace("\"currency\": \"BRL\"", "\"currency\": \"BRL\", \"x\": 1"))))
        .isNotEmpty();
  }
}

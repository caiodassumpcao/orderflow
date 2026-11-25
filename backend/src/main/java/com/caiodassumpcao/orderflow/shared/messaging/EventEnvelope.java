package com.caiodassumpcao.orderflow.shared.messaging;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * Envelope publicado no Kafka (contrato em {@code docs/events/}).
 *
 * @param eventId id único (UUIDv7); chave de deduplicação dos consumers
 * @param type nome do evento
 * @param version versão do contrato do payload
 * @param aggregateId id do agregado; também é a chave da mensagem
 * @param aggregateVersion versão do agregado depois do evento; consumers aplicam só o que é mais
 *     novo do que já viram
 * @param occurredAt quando o fato aconteceu
 * @param correlationId id da requisição de origem, se houver
 * @param payload conteúdo específico do evento
 */
public record EventEnvelope(
    UUID eventId,
    String type,
    int version,
    UUID aggregateId,
    long aggregateVersion,
    Instant occurredAt,
    String correlationId,
    JsonNode payload) {

  public EventEnvelope {
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(aggregateId, "aggregateId");
    if (aggregateVersion < 0) {
      throw new IllegalArgumentException("aggregateVersion must not be negative");
    }
    Objects.requireNonNull(occurredAt, "occurredAt");
    Objects.requireNonNull(payload, "payload");
  }
}

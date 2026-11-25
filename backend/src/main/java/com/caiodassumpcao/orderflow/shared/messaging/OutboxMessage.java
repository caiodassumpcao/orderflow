package com.caiodassumpcao.orderflow.shared.messaging;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Linha da outbox: os campos do envelope do evento e o payload em JSON.
 *
 * @param eventId id único do evento (UUIDv7), usado pelos consumers para deduplicar
 * @param topic tópico Kafka de destino
 * @param aggregateType tipo do agregado (ex.: {@code Order})
 * @param aggregateId id do agregado; também é a chave de partição no Kafka
 * @param aggregateVersion versão do agregado depois do evento (crescente por agregado)
 * @param eventType nome do evento (ex.: {@code OrderCreated})
 * @param eventVersion versão do contrato do payload
 * @param payloadJson payload serializado
 * @param correlationId id de correlação da requisição de origem, se houver
 * @param occurredAt quando o fato aconteceu no domínio
 */
public record OutboxMessage(
    UUID eventId,
    String topic,
    String aggregateType,
    UUID aggregateId,
    long aggregateVersion,
    String eventType,
    int eventVersion,
    String payloadJson,
    String correlationId,
    Instant occurredAt) {

  public OutboxMessage {
    Objects.requireNonNull(eventId);
    Objects.requireNonNull(topic);
    Objects.requireNonNull(aggregateType);
    Objects.requireNonNull(aggregateId);
    if (aggregateVersion < 0) {
      throw new IllegalArgumentException("aggregateVersion must not be negative");
    }
    Objects.requireNonNull(eventType);
    Objects.requireNonNull(payloadJson);
    Objects.requireNonNull(occurredAt);
  }
}

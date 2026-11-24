package com.caiodassumpcao.orderflow.shared.messaging;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grava mensagens na tabela {@code command.outbox}. Exige uma transação em andamento: a mensagem só
 * existe se a mudança de estado que a gerou for confirmada.
 */
@Component
public class OutboxWriter {

  private static final String INSERT =
      """
      INSERT INTO command.outbox
          (id, topic, aggregate_type, aggregate_id, event_type, event_version, payload,
           correlation_id, occurred_at)
      VALUES
          (:id, :topic, :aggregateType, :aggregateId, :eventType, :eventVersion,
           CAST(:payload AS jsonb),
           :correlationId, :occurredAt)
      """;

  private final JdbcClient jdbc;

  public OutboxWriter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void append(OutboxMessage message) {
    jdbc.sql(INSERT)
        .param("id", message.eventId())
        .param("topic", message.topic())
        .param("aggregateType", message.aggregateType())
        .param("aggregateId", message.aggregateId())
        .param("eventType", message.eventType())
        .param("eventVersion", message.eventVersion())
        .param("payload", message.payloadJson())
        .param("correlationId", message.correlationId())
        .param("occurredAt", OffsetDateTime.ofInstant(message.occurredAt(), ZoneOffset.UTC))
        .update();
  }
}

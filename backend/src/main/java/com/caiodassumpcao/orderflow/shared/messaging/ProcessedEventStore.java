package com.caiodassumpcao.orderflow.shared.messaging;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Registro de {@code (eventId, consumer)} em {@code command.processed_events}. */
@Component
public class ProcessedEventStore {

  private final JdbcClient jdbc;

  public ProcessedEventStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Marca o evento como processado pelo consumer.
   *
   * @return {@code false} se já estava marcado (reentrega); a chave primária garante a resposta
   *     correta mesmo com duas entregas simultâneas
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public boolean markProcessed(UUID eventId, String consumer) {
    return jdbc.sql(
                """
                INSERT INTO command.processed_events (event_id, consumer)
                VALUES (:eventId, :consumer)
                ON CONFLICT DO NOTHING
                """)
            .param("eventId", eventId)
            .param("consumer", consumer)
            .update()
        == 1;
  }
}

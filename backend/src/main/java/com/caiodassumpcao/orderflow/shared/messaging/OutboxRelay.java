package com.caiodassumpcao.orderflow.shared.messaging;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publica no Kafka os eventos pendentes da outbox, em ordem de {@code position}.
 *
 * <ul>
 *   <li>Uma instância por vez: um advisory lock transacional do PostgreSQL impede que duas
 *       instâncias publiquem lotes em paralelo e invertam a ordem dos eventos de um agregado.
 *   <li>A mensagem só é marcada como publicada depois da confirmação do broker; na primeira falha o
 *       lote para e só o prefixo confirmado é marcado. A entrega é at-least-once: os consumers
 *       deduplicam pelo {@code eventId}.
 * </ul>
 */
@Component
public class OutboxRelay {

  private static final Logger LOG = LoggerFactory.getLogger(OutboxRelay.class);

  /** Chave do advisory lock que elege a instância publicadora. */
  static final long RELAY_LOCK_KEY = 0x0F_0F10_0000_0001L;

  private final JdbcClient jdbc;
  private final KafkaOperations<String, String> kafka;
  private final JsonMapper json;
  private final EventEnvelopeCodec codec;
  private final TransactionTemplate transactions;
  private final MessagingProperties.Relay properties;
  private final Counter published;
  private final Counter failures;

  public OutboxRelay(
      JdbcClient jdbc,
      KafkaOperations<String, String> kafka,
      JsonMapper json,
      EventEnvelopeCodec codec,
      PlatformTransactionManager transactionManager,
      MessagingProperties properties,
      MeterRegistry meters) {
    this.jdbc = jdbc;
    this.kafka = kafka;
    this.json = json;
    this.codec = codec;
    this.transactions = new TransactionTemplate(transactionManager);
    this.properties = properties.relay();
    this.published =
        Counter.builder("orderflow.outbox.published")
            .description("Eventos da outbox confirmados pelo broker")
            .register(meters);
    this.failures =
        Counter.builder("orderflow.outbox.publish.failures")
            .description("Falhas ao publicar eventos da outbox (o evento fica pendente)")
            .register(meters);
    Gauge.builder("orderflow.outbox.pending", this, OutboxRelay::pendingCount)
        .description("Eventos da outbox ainda não publicados")
        .register(meters);
  }

  /**
   * Publica um lote. Devolve quantos eventos foram confirmados; 0 se não havia pendências, se outra
   * instância está publicando ou se o primeiro envio falhou.
   */
  public int publishBatch() {
    Integer count = transactions.execute(status -> lockAndPublish());
    return count == null ? 0 : count;
  }

  /** Publica lotes até esvaziar a outbox ou encontrar uma falha. */
  public void drain() {
    int count;
    do {
      count = publishBatch();
    } while (count == properties.batchSize());
  }

  private int lockAndPublish() {
    Boolean locked =
        jdbc.sql("SELECT pg_try_advisory_xact_lock(:key)")
            .param("key", RELAY_LOCK_KEY)
            .query(Boolean.class)
            .single();
    if (!Boolean.TRUE.equals(locked)) {
      return 0;
    }
    List<PendingEvent> pending = loadPending();
    if (pending.isEmpty()) {
      return 0;
    }
    List<CompletableFuture<SendResult<String, String>>> sends = new ArrayList<>(pending.size());
    for (PendingEvent event : pending) {
      sends.add(send(event));
    }
    List<UUID> confirmed = new ArrayList<>(pending.size());
    for (int i = 0; i < pending.size(); i++) {
      if (!awaitConfirmation(sends.get(i), pending.get(i))) {
        break;
      }
      confirmed.add(pending.get(i).envelope().eventId());
    }
    markPublished(confirmed);
    published.increment(confirmed.size());
    return confirmed.size();
  }

  /** O produtor pode falhar já no envio (ex.: metadados indisponíveis); vira um futuro com erro. */
  private CompletableFuture<SendResult<String, String>> send(PendingEvent event) {
    try {
      return kafka.send(
          new ProducerRecord<>(
              event.topic(),
              event.envelope().aggregateId().toString(),
              codec.encode(event.envelope())));
    } catch (RuntimeException e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  private boolean awaitConfirmation(
      CompletableFuture<SendResult<String, String>> send, PendingEvent event) {
    try {
      send.get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
      return true;
    } catch (ExecutionException | TimeoutException e) {
      failures.increment();
      LOG.warn(
          "Outbox event {} not confirmed by the broker; it stays pending and will be retried",
          event.envelope().eventId(),
          e);
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  private List<PendingEvent> loadPending() {
    return jdbc.sql(
            """
            SELECT id, topic, event_type, event_version, aggregate_id, occurred_at,
                   correlation_id, payload::text AS payload
              FROM command.outbox
             WHERE published_at IS NULL
             ORDER BY position
             LIMIT :limit
            """)
        .param("limit", properties.batchSize())
        .query((rs, row) ->
            new PendingEvent(
                rs.getString("topic"),
                new EventEnvelope(
                    rs.getObject("id", UUID.class),
                    rs.getString("event_type"),
                    rs.getInt("event_version"),
                    rs.getObject("aggregate_id", UUID.class),
                    rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                    rs.getString("correlation_id"),
                    json.readTree(rs.getString("payload")))))
        .list();
  }

  private void markPublished(List<UUID> eventIds) {
    if (eventIds.isEmpty()) {
      return;
    }
    jdbc.sql("UPDATE command.outbox SET published_at = :now WHERE id IN (:ids)")
        .param("now", OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC))
        .param("ids", eventIds)
        .update();
  }

  private double pendingCount() {
    return jdbc.sql("SELECT count(*) FROM command.outbox WHERE published_at IS NULL")
        .query(Long.class)
        .single();
  }

  private record PendingEvent(String topic, EventEnvelope envelope) {}
}

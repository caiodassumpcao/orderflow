package com.caiodassumpcao.orderflow.shared.messaging;

import com.caiodassumpcao.orderflow.shared.observability.CorrelationIdFilter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Base dos consumers: decodifica o envelope, propaga o {@code correlationId} para os logs e executa
 * o handler no máximo uma vez por {@code (eventId, consumer)}. A marcação em {@code
 * processed_events} e os efeitos do handler são confirmados na mesma transação; se o handler
 * falhar, nada fica gravado e a mensagem segue para o retry.
 */
@Component
public class IdempotentEventProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(IdempotentEventProcessor.class);

  private final EventEnvelopeCodec codec;
  private final ProcessedEventStore processedEvents;
  private final TransactionTemplate transactions;
  private final MeterRegistry meters;

  public IdempotentEventProcessor(
      EventEnvelopeCodec codec,
      ProcessedEventStore processedEvents,
      PlatformTransactionManager transactionManager,
      MeterRegistry meters) {
    this.codec = codec;
    this.processedEvents = processedEvents;
    this.transactions = new TransactionTemplate(transactionManager);
    this.meters = meters;
  }

  /**
   * Processa uma mensagem recebida.
   *
   * @param consumer nome estável do consumer (também é o group id e o sufixo dos tópicos de retry)
   * @param message valor da mensagem Kafka
   * @param handler efeito do consumer; roda dentro da transação
   * @return {@code false} se o evento já tinha sido processado por este consumer
   * @throws PoisonMessageException se a mensagem não for um envelope válido
   */
  public boolean process(String consumer, String message, Consumer<EventEnvelope> handler) {
    EventEnvelope envelope = codec.decode(message);
    String previousCorrelationId = MDC.get(CorrelationIdFilter.MDC_KEY);
    if (envelope.correlationId() != null) {
      MDC.put(CorrelationIdFilter.MDC_KEY, envelope.correlationId());
    }
    try {
      Boolean processed =
          transactions.execute(status -> {
            if (!processedEvents.markProcessed(envelope.eventId(), consumer)) {
              return false;
            }
            handler.accept(envelope);
            return true;
          });
      boolean firstDelivery = Boolean.TRUE.equals(processed);
      meters
          .counter(
              firstDelivery ? "orderflow.messaging.processed" : "orderflow.messaging.duplicates",
              "consumer",
              consumer)
          .increment();
      if (!firstDelivery) {
        LOG.info(
            "Skipping duplicate event {} ({}) for consumer {}",
            envelope.eventId(),
            envelope.type(),
            consumer);
      }
      return firstDelivery;
    } finally {
      restoreCorrelationId(previousCorrelationId);
    }
  }

  private static void restoreCorrelationId(String previous) {
    if (previous == null) {
      MDC.remove(CorrelationIdFilter.MDC_KEY);
    } else {
      MDC.put(CorrelationIdFilter.MDC_KEY, previous);
    }
  }
}

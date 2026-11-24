package com.caiodassumpcao.orderflow.shared.messaging;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Registro das mensagens que chegaram à DLQ: log de erro (sem o conteúdo da mensagem, que pode ter
 * dados pessoais) e a métrica {@code orderflow.messaging.dead.letters}, base dos alertas.
 *
 * <p>Cada consumer chama {@link #record} no seu método {@code @DltHandler}.
 */
@Component
public class DeadLetters {

  private static final Logger LOG = LoggerFactory.getLogger(DeadLetters.class);

  private final MeterRegistry meters;

  public DeadLetters(MeterRegistry meters) {
    this.meters = meters;
  }

  /**
   * Registra uma mensagem na DLQ.
   *
   * @param consumer consumer que desistiu da mensagem
   * @param topic tópico DLQ em que ela está
   * @param key chave da mensagem (id do agregado)
   * @param reason mensagem da exceção que levou à DLQ, se disponível
   */
  public void record(String consumer, String topic, String key, String reason) {
    meters.counter("orderflow.messaging.dead.letters", "consumer", consumer).increment();
    LOG.error(
        "Message with key {} moved to dead-letter topic {} by consumer {}: {}",
        key,
        topic,
        consumer,
        reason == null ? "unknown reason" : reason);
  }
}

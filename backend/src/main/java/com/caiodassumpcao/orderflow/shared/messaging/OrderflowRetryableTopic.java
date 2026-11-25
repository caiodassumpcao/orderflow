package com.caiodassumpcao.orderflow.shared.messaging;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.core.annotation.AliasFor;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;

/**
 * Política de retry não bloqueante dos consumers do OrderFlow: backoff exponencial em tópicos de
 * retry próprios do consumer e DLQ depois da última tentativa. Mensagens envenenadas ({@link
 * PoisonMessageException}) vão direto para a DLQ.
 *
 * <p>Uso, junto do {@code @KafkaListener}:
 *
 * <pre>{@code
 * @OrderflowRetryableTopic(
 *     retryTopicSuffix = ".projector.retry",
 *     dltTopicSuffix = ".projector.dlq")
 * @KafkaListener(topics = OrderTopics.ORDER_EVENTS, groupId = "projector")
 * }</pre>
 *
 * Os sufixos levam o nome do consumer porque cada grupo precisa dos próprios tópicos de retry:
 * {@code order.events.projector.retry-0}, {@code -1}, ... e {@code order.events.projector.dlq}.
 *
 * <p>Configuração ({@code orderflow.messaging.retry.*}): {@code attempts} (tentativas no total,
 * padrão 4), {@code initial-delay-ms} (1000), {@code multiplier} (2.0) e {@code max-delay-ms}
 * (10000).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@RetryableTopic(
    attempts = "${orderflow.messaging.retry.attempts:4}",
    backOff =
        @BackOff(
            delayString = "${orderflow.messaging.retry.initial-delay-ms:1000}",
            multiplierString = "${orderflow.messaging.retry.multiplier:2.0}",
            maxDelayString = "${orderflow.messaging.retry.max-delay-ms:10000}"),
    exclude = PoisonMessageException.class,
    topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
    autoCreateTopics = "true",
    numPartitions = "${orderflow.messaging.topics.partitions:3}",
    replicationFactor = "${orderflow.messaging.topics.replication-factor:1}")
public @interface OrderflowRetryableTopic {

  /** Sufixo dos tópicos de retry, com o nome do consumer (ex.: {@code .projector.retry}). */
  @AliasFor(annotation = RetryableTopic.class, attribute = "retryTopicSuffix")
  String retryTopicSuffix();

  /** Sufixo da DLQ, com o nome do consumer (ex.: {@code .projector.dlq}). */
  @AliasFor(annotation = RetryableTopic.class, attribute = "dltTopicSuffix")
  String dltTopicSuffix();
}

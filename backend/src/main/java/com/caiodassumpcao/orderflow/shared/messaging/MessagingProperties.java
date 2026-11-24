package com.caiodassumpcao.orderflow.shared.messaging;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuração da mensageria ({@code orderflow.messaging.*}). A política de retry dos consumers é
 * lida direto pela {@link OrderflowRetryableTopic}.
 *
 * @param relay publicação da outbox
 * @param topics criação dos tópicos
 */
@ConfigurationProperties("orderflow.messaging")
public record MessagingProperties(@DefaultValue Relay relay, @DefaultValue Topics topics) {

  /**
   * Relay da outbox.
   *
   * @param enabled liga o agendamento (desligado nos testes que não tratam de mensageria)
   * @param interval intervalo entre varreduras da outbox
   * @param batchSize máximo de eventos publicados por varredura
   * @param sendTimeout espera máxima pela confirmação do broker para cada evento
   */
  public record Relay(
      @DefaultValue("true") boolean enabled,
      @DefaultValue("500ms") Duration interval,
      @DefaultValue("100") int batchSize,
      @DefaultValue("30s") Duration sendTimeout) {}

  /**
   * Tópicos criados pela aplicação.
   *
   * @param partitions partições de cada tópico principal
   * @param replicationFactor réplicas (1 em dev; 3 em produção)
   */
  public record Topics(
      @DefaultValue("3") int partitions, @DefaultValue("1") short replicationFactor) {}
}

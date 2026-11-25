package com.caiodassumpcao.orderflow.orders.adapter.messaging;

import com.caiodassumpcao.orderflow.shared.messaging.MessagingProperties;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Tópicos publicados pelo contexto de pedidos. O broker não cria tópicos sozinho ({@code
 * auto.create.topics.enable=false}); o {@code KafkaAdmin} os cria na subida.
 */
@Configuration(proxyBeanMethods = false)
public class OrderTopics {

  /** Eventos do agregado Order, particionados por {@code aggregateId}. */
  public static final String ORDER_EVENTS = "order.events";

  @Bean
  NewTopic orderEventsTopic(MessagingProperties properties) {
    return TopicBuilder.name(ORDER_EVENTS)
        .partitions(properties.topics().partitions())
        .replicas(properties.topics().replicationFactor())
        .build();
  }
}

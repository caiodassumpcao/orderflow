package com.caiodassumpcao.orderflow.inventory.adapter.messaging;

import com.caiodassumpcao.orderflow.shared.messaging.MessagingProperties;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Tópicos do contexto de inventário. */
@Configuration(proxyBeanMethods = false)
public class InventoryTopics {

  /**
   * Eventos publicados pelo inventário (resposta à saga de pedidos), chaveados pelo id do pedido.
   */
  public static final String INVENTORY_EVENTS = "inventory.events";

  /** Tópico de pedidos consumido pelo inventário (contrato em docs/events/order-events.md). */
  static final String ORDER_EVENTS = "order.events";

  @Bean
  NewTopic inventoryEventsTopic(MessagingProperties properties) {
    return TopicBuilder.name(INVENTORY_EVENTS)
        .partitions(properties.topics().partitions())
        .replicas(properties.topics().replicationFactor())
        .build();
  }
}

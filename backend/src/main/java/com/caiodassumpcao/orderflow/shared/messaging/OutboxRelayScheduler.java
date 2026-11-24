package com.caiodassumpcao.orderflow.shared.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Dispara o {@link OutboxRelay} periodicamente; desligável com {@code
 * orderflow.messaging.relay.enabled}.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnBooleanProperty(name = "orderflow.messaging.relay.enabled", matchIfMissing = true)
public class OutboxRelayScheduler {

  private final OutboxRelay relay;

  public OutboxRelayScheduler(OutboxRelay relay) {
    this.relay = relay;
  }

  @Scheduled(fixedDelayString = "${orderflow.messaging.relay.interval:500ms}")
  void relay() {
    relay.drain();
  }
}

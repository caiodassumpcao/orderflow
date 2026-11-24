package com.caiodassumpcao.orderflow.orders.adapter.config;

import com.caiodassumpcao.orderflow.orders.application.OrderCommandService;
import com.caiodassumpcao.orderflow.orders.application.port.IdempotencyKeyStore;
import com.caiodassumpcao.orderflow.orders.application.port.OrderEventOutbox;
import com.caiodassumpcao.orderflow.orders.application.port.OrderRepository;
import com.caiodassumpcao.orderflow.orders.application.port.UnitOfWork;
import com.caiodassumpcao.orderflow.orders.domain.OrderId;
import com.caiodassumpcao.orderflow.shared.id.UuidV7;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Liga os casos de uso (Java puro) às implementações das portas. */
@Configuration(proxyBeanMethods = false)
public class OrdersConfiguration {

  @Bean
  OrderCommandService orderCommandService(
      OrderRepository orders,
      OrderEventOutbox outbox,
      IdempotencyKeyStore idempotencyKeys,
      UnitOfWork unitOfWork,
      Clock clock) {
    return new OrderCommandService(
        orders,
        outbox,
        idempotencyKeys,
        unitOfWork,
        clock,
        () -> new OrderId(UuidV7.generate(clock)));
  }
}

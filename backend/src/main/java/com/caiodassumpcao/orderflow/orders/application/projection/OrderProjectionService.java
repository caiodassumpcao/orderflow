package com.caiodassumpcao.orderflow.orders.application.projection;

import com.caiodassumpcao.orderflow.orders.application.port.OrderProjectionStore;
import java.util.Objects;

/**
 * Mantém o modelo de leitura de pedidos a partir dos eventos. Tolerante a eventos repetidos e fora
 * de ordem: a deduplicação é feita antes (por {@code eventId}) e a ordem é resolvida pela {@code
 * aggregateVersion}.
 */
public class OrderProjectionService {

  private final OrderProjectionStore store;

  public OrderProjectionService(OrderProjectionStore store) {
    this.store = Objects.requireNonNull(store);
  }

  public void apply(ProjectionEvent event) {
    switch (event) {
      case ProjectionEvent.OrderCreated created -> store.applyCreated(created);
      case ProjectionEvent.StatusChanged changed -> store.applyStatusChange(changed);
    }
  }
}

package com.caiodassumpcao.orderflow.orders.application.port;

import com.caiodassumpcao.orderflow.orders.application.projection.ProjectionEvent;

/**
 * Gravação da projeção de pedidos. Cada operação deve ser atômica e independente da ordem de
 * chegada: o status final é sempre o do evento de maior {@code aggregateVersion}, e os detalhes do
 * {@code OrderCreated} são preenchidos mesmo que ele chegue depois de um evento de status.
 */
public interface OrderProjectionStore {

  void applyCreated(ProjectionEvent.OrderCreated event);

  void applyStatusChange(ProjectionEvent.StatusChanged event);
}

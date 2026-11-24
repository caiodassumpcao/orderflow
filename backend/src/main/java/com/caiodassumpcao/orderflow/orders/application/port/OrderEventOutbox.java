package com.caiodassumpcao.orderflow.orders.application.port;

import com.caiodassumpcao.orderflow.orders.domain.OrderEvent;
import java.util.List;

/**
 * Transactional Outbox: grava os eventos na mesma transação do agregado. A publicação no broker é
 * feita depois, de forma assíncrona, por um relay.
 */
public interface OrderEventOutbox {

  void append(List<OrderEvent> events);
}

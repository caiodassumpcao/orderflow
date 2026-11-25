package com.caiodassumpcao.orderflow.inventory.application.port;

import com.caiodassumpcao.orderflow.inventory.domain.InventoryEvent;
import java.util.List;

/** Grava os eventos do inventário na outbox, na mesma transação da reserva. */
public interface InventoryEventOutbox {

  void append(List<InventoryEvent> events);
}

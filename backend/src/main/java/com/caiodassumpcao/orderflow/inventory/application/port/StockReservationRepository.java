package com.caiodassumpcao.orderflow.inventory.application.port;

import com.caiodassumpcao.orderflow.inventory.domain.StockReservation;
import java.util.Optional;
import java.util.UUID;

/** Reservas de estoque por pedido. */
public interface StockReservationRepository {

  /** Reserva do pedido, travada para atualização até o fim da transação. */
  Optional<StockReservation> lockByOrderId(UUID orderId);

  /** Insere uma reserva nova ou atualiza o status de uma existente. */
  void save(StockReservation reservation);
}

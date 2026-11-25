package com.caiodassumpcao.orderflow.inventory.domain;

/** Situação da reserva de estoque de um pedido. */
public enum ReservationStatus {
  /** Estoque reservado para o pedido. */
  RESERVED,
  /** Pedido recusado (falta de estoque, SKU desconhecido ou preço divergente). */
  REJECTED,
  /** Reserva desfeita porque o pedido foi cancelado; estoque devolvido. */
  RELEASED,
  /** Pedido enviado; estoque reservado baixado. */
  FULFILLED,
  /**
   * O cancelamento chegou antes da criação do pedido (eventos fora de ordem): nada foi reservado e
   * a criação, quando chegar, é ignorada.
   */
  CANCELLED_BEFORE_RESERVATION
}

package com.caiodassumpcao.orderflow.orders.application.port;

import com.caiodassumpcao.orderflow.orders.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Currency;
import java.util.List;

/**
 * Agregados do modelo de leitura para o dashboard. Pedidos ainda sem o {@code OrderCreated}
 * projetado não entram nas contas.
 */
public interface OrderStatistics {

  /** Quantidade e soma dos totais por status e moeda. */
  List<StatusTotal> totalsByStatus();

  /**
   * Pedidos criados por dia, a partir de {@code from}, com o dia calculado no fuso {@code zone}.
   * Dias sem pedidos não aparecem.
   */
  List<DailyOrders> dailyOrders(Instant from, ZoneId zone);

  /** Pedidos de um status numa moeda. */
  record StatusTotal(OrderStatus status, Currency currency, long orders, BigDecimal amount) {}

  /**
   * Pedidos criados num dia.
   *
   * @param cancelled quantos desses pedidos estão cancelados hoje
   */
  record DailyOrders(LocalDate date, long orders, long cancelled) {}
}

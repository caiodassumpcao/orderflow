package com.caiodassumpcao.orderflow.orders.application.query;

import com.caiodassumpcao.orderflow.orders.application.port.OrderStatistics.DailyOrders;
import com.caiodassumpcao.orderflow.orders.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Currency;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Visão geral dos pedidos para a operação.
 *
 * @param ordersByStatus quantidade de pedidos em cada status (todos os status, zero se vazio)
 * @param confirmedRevenue soma dos pedidos confirmados ou enviados, por moeda
 * @param daily pedidos criados por dia no período, do mais antigo para hoje, sem lacunas
 */
public record DashboardSummary(
    Instant generatedAt,
    ZoneId zone,
    long totalOrders,
    Map<OrderStatus, Long> ordersByStatus,
    List<Revenue> confirmedRevenue,
    List<DailyOrders> daily) {

  public DashboardSummary {
    ordersByStatus = Collections.unmodifiableMap(new EnumMap<>(ordersByStatus));
    confirmedRevenue = List.copyOf(confirmedRevenue);
    daily = List.copyOf(daily);
  }

  /** Receita numa moeda. */
  public record Revenue(Currency currency, BigDecimal amount) {}
}

package com.caiodassumpcao.orderflow.orders.adapter.persistence;

import com.caiodassumpcao.orderflow.orders.application.port.OrderStatistics;
import com.caiodassumpcao.orderflow.orders.domain.OrderStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agregados sobre {@code query.order_summaries}. Agrega na consulta, sem tabela própria; uma
 * projeção agregada para volumes grandes é estudada na Fase 8.
 */
@Repository
@Transactional(readOnly = true)
class JdbcOrderStatistics implements OrderStatistics {

  private final JdbcClient jdbc;

  JdbcOrderStatistics(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<StatusTotal> totalsByStatus() {
    return jdbc.sql(
            """
            SELECT status, currency, count(*) AS orders, sum(total_amount) AS amount
              FROM query.order_summaries
             WHERE customer_id IS NOT NULL
             GROUP BY status, currency
            """)
        .query((rs, row) ->
            new StatusTotal(
                OrderStatus.valueOf(rs.getString("status")),
                Currency.getInstance(rs.getString("currency")),
                rs.getLong("orders"),
                rs.getBigDecimal("amount")))
        .list();
  }

  @Override
  public List<DailyOrders> dailyOrders(Instant from, ZoneId zone) {
    // O dia de cada pedido é o da data local no fuso pedido; o filtro em created_at usa o índice
    // parcial ix_order_summaries_created (V9).
    return jdbc.sql(
            """
            SELECT (created_at AT TIME ZONE :zone)::date AS day,
                   count(*) AS orders,
                   count(*) FILTER (WHERE status = 'CANCELLED') AS cancelled
              FROM query.order_summaries
             WHERE customer_id IS NOT NULL AND created_at >= :from
             GROUP BY day
             ORDER BY day
            """)
        .param("zone", zone.getId())
        .param("from", OffsetDateTime.ofInstant(from, ZoneOffset.UTC))
        .query((rs, row) ->
            new DailyOrders(
                rs.getObject("day", LocalDate.class),
                rs.getLong("orders"),
                rs.getLong("cancelled")))
        .list();
  }
}

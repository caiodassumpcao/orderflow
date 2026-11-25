package com.caiodassumpcao.orderflow.orders.adapter.persistence;

import com.caiodassumpcao.orderflow.orders.application.port.OrderReadModel;
import com.caiodassumpcao.orderflow.orders.application.query.OrderDetail;
import com.caiodassumpcao.orderflow.orders.application.query.OrderSummary;
import com.caiodassumpcao.orderflow.orders.application.query.PageCursor;
import com.caiodassumpcao.orderflow.orders.domain.CustomerId;
import com.caiodassumpcao.orderflow.orders.domain.OrderId;
import com.caiodassumpcao.orderflow.orders.domain.OrderStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultas em {@code query.order_summaries}. A listagem usa paginação keyset sobre {@code
 * (created_at, order_id)} decrescentes, atendida pelo índice {@code
 * ix_order_summaries_customer_created}.
 */
@Repository
@Transactional(readOnly = true)
class JdbcOrderReadModel implements OrderReadModel {

  private static final String SUMMARY_COLUMNS =
      "order_id, customer_id, status, currency, total_amount, item_count, created_at, updated_at";

  private final JdbcClient jdbc;

  JdbcOrderReadModel(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<OrderSummary> findOrders(
      Optional<CustomerId> customerId,
      Optional<OrderStatus> status,
      Optional<PageCursor> after,
      int limit) {
    // Só fragmentos fixos são concatenados; todo valor vai por parâmetro. "customer_id IS NOT NULL"
    // esconde pedidos cujo OrderCreated ainda não foi projetado e casa com o índice parcial (V9).
    StringBuilder sql =
        new StringBuilder("SELECT ")
            .append(SUMMARY_COLUMNS)
            .append(" FROM query.order_summaries WHERE customer_id IS NOT NULL");
    customerId.ifPresent(c -> sql.append(" AND customer_id = :customerId"));
    status.ifPresent(s -> sql.append(" AND status = :status"));
    after.ifPresent(c -> sql.append(" AND (created_at, order_id) < (:afterCreatedAt, :afterId)"));
    sql.append(" ORDER BY created_at DESC, order_id DESC LIMIT :limit");

    JdbcClient.StatementSpec statement = jdbc.sql(sql.toString()).param("limit", limit);
    if (customerId.isPresent()) {
      statement = statement.param("customerId", customerId.get().value());
    }
    if (status.isPresent()) {
      statement = statement.param("status", status.get().name());
    }
    if (after.isPresent()) {
      statement =
          statement
              .param(
                  "afterCreatedAt",
                  OffsetDateTime.ofInstant(after.get().createdAt(), ZoneOffset.UTC))
              .param("afterId", after.get().orderId().value());
    }
    return statement.query((rs, row) -> summary(rs)).list();
  }

  @Override
  public Optional<OrderDetail> findOrder(OrderId orderId, Optional<CustomerId> customerId) {
    record Row(OrderSummary summary, String cancellationReason) {}
    String sql =
        "SELECT "
            + SUMMARY_COLUMNS
            + ", cancellation_reason FROM query.order_summaries"
            + " WHERE order_id = :orderId AND customer_id IS NOT NULL"
            + (customerId.isPresent() ? " AND customer_id = :customerId" : "");
    JdbcClient.StatementSpec statement = jdbc.sql(sql).param("orderId", orderId.value());
    if (customerId.isPresent()) {
      statement = statement.param("customerId", customerId.get().value());
    }
    Optional<Row> row =
        statement
            .query((rs, n) -> new Row(summary(rs), rs.getString("cancellation_reason")))
            .optional();
    return row.map(
        found -> new OrderDetail(found.summary(), items(orderId), found.cancellationReason()));
  }

  private List<OrderDetail.Item> items(OrderId orderId) {
    return jdbc.sql(
            "SELECT sku, quantity, unit_price FROM query.order_items"
                + " WHERE order_id = :orderId ORDER BY line_number")
        .param("orderId", orderId.value())
        .query((rs, n) ->
            new OrderDetail.Item(
                rs.getString("sku"), rs.getInt("quantity"), rs.getBigDecimal("unit_price")))
        .list();
  }

  private static OrderSummary summary(ResultSet rs) throws SQLException {
    return new OrderSummary(
        new OrderId(rs.getObject("order_id", UUID.class)),
        new CustomerId(rs.getString("customer_id")),
        OrderStatus.valueOf(rs.getString("status")),
        Currency.getInstance(rs.getString("currency")),
        rs.getBigDecimal("total_amount"),
        rs.getInt("item_count"),
        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
        rs.getObject("updated_at", OffsetDateTime.class).toInstant());
  }
}

package com.caiodassumpcao.orderflow.inventory.adapter.persistence;

import com.caiodassumpcao.orderflow.inventory.application.port.StockReservationRepository;
import com.caiodassumpcao.orderflow.inventory.domain.RejectionReason;
import com.caiodassumpcao.orderflow.inventory.domain.ReservationLine;
import com.caiodassumpcao.orderflow.inventory.domain.ReservationStatus;
import com.caiodassumpcao.orderflow.inventory.domain.Sku;
import com.caiodassumpcao.orderflow.inventory.domain.StockReservation;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Reservas em {@code command.stock_reservations} e {@code command.stock_reservation_lines}. */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
class JdbcStockReservationRepository implements StockReservationRepository {

  private final JdbcClient jdbc;

  JdbcStockReservationRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  private record Header(
      ReservationStatus status,
      String reason,
      Instant createdAt,
      Instant updatedAt,
      long version) {}

  @Override
  public Optional<StockReservation> lockByOrderId(UUID orderId) {
    Optional<Header> header =
        jdbc.sql(
                """
                SELECT status, rejection_reason, created_at, updated_at, version
                  FROM command.stock_reservations
                 WHERE order_id = :orderId
                   FOR UPDATE
                """)
            .param("orderId", orderId)
            .query((rs, row) ->
                new Header(
                    ReservationStatus.valueOf(rs.getString("status")),
                    rs.getString("rejection_reason"),
                    rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                    rs.getObject("updated_at", OffsetDateTime.class).toInstant(),
                    rs.getLong("version")))
            .optional();
    return header.map(h ->
        StockReservation.rehydrate(
            orderId,
            lines(orderId),
            h.status(),
            h.reason() == null ? null : RejectionReason.valueOf(h.reason()),
            h.createdAt(),
            h.updatedAt(),
            h.version()));
  }

  @Override
  public void save(StockReservation reservation) {
    if (reservation.version().isEmpty()) {
      insert(reservation);
      return;
    }
    int updated =
        jdbc.sql(
                """
                UPDATE command.stock_reservations
                   SET status = :status, updated_at = :updatedAt, version = version + 1
                 WHERE order_id = :orderId AND version = :version
                """)
            .param("status", reservation.status().name())
            .param("updatedAt", utc(reservation.updatedAt()))
            .param("orderId", reservation.orderId())
            .param("version", reservation.version().get())
            .update();
    if (updated != 1) {
      throw new OptimisticLockingFailureException(
          "Reservation of order " + reservation.orderId() + " changed");
    }
  }

  private void insert(StockReservation reservation) {
    jdbc.sql(
            """
            INSERT INTO command.stock_reservations
                (order_id, status, rejection_reason, created_at, updated_at, version)
            VALUES (:orderId, :status, :reason, :createdAt, :updatedAt, 0)
            """)
        .param("orderId", reservation.orderId())
        .param("status", reservation.status().name())
        .param("reason", reservation.rejectionReason().map(Enum::name).orElse(null))
        .param("createdAt", utc(reservation.createdAt()))
        .param("updatedAt", utc(reservation.updatedAt()))
        .update();
    List<ReservationLine> lines = reservation.lines();
    for (int i = 0; i < lines.size(); i++) {
      ReservationLine line = lines.get(i);
      jdbc.sql(
              """
              INSERT INTO command.stock_reservation_lines
                  (order_id, line_number, sku, quantity, unit_price)
              VALUES (:orderId, :lineNumber, :sku, :quantity, :unitPrice)
              """)
          .param("orderId", reservation.orderId())
          .param("lineNumber", i)
          .param("sku", line.sku().value())
          .param("quantity", line.quantity())
          .param("unitPrice", line.unitPrice())
          .update();
    }
  }

  private List<ReservationLine> lines(UUID orderId) {
    return jdbc.sql(
            """
            SELECT sku, quantity, unit_price
              FROM command.stock_reservation_lines
             WHERE order_id = :orderId
             ORDER BY line_number
            """)
        .param("orderId", orderId)
        .query((rs, row) ->
            new ReservationLine(
                new Sku(rs.getString("sku")),
                rs.getInt("quantity"),
                rs.getBigDecimal("unit_price")))
        .list();
  }

  private static OffsetDateTime utc(Instant instant) {
    return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }
}

package com.caiodassumpcao.orderflow.orders.adapter.persistence;

import com.caiodassumpcao.orderflow.orders.application.port.OrderProjectionStore;
import com.caiodassumpcao.orderflow.orders.application.projection.ProjectionEvent;
import com.caiodassumpcao.orderflow.orders.domain.OrderStatus;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Projeção em {@code query.order_summaries} e {@code query.order_items}. Cada evento vira um único
 * upsert com guarda de versão, atômico mesmo com eventos do mesmo pedido processados em paralelo
 * (tópico principal e tópicos de retry).
 */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
class JdbcOrderProjectionStore implements OrderProjectionStore {

  /**
   * Preenche os detalhes sempre; o status só muda se o OrderCreated for mais novo que o que já foi
   * aplicado (na prática, quando a linha ainda não existe).
   */
  private static final String UPSERT_CREATED =
      """
      INSERT INTO query.order_summaries AS s
          (order_id, customer_id, status, currency, total_amount, item_count,
           created_at, updated_at, last_event_version)
      VALUES
          (:orderId, :customerId, :status, :currency, :total, :itemCount,
           :occurredAt, :occurredAt, :version)
      ON CONFLICT (order_id) DO UPDATE SET
          customer_id = EXCLUDED.customer_id,
          currency = EXCLUDED.currency,
          total_amount = EXCLUDED.total_amount,
          item_count = EXCLUDED.item_count,
          created_at = EXCLUDED.created_at,
          status = CASE WHEN EXCLUDED.last_event_version > s.last_event_version
                        THEN EXCLUDED.status ELSE s.status END,
          updated_at = CASE WHEN EXCLUDED.last_event_version > s.last_event_version
                            THEN EXCLUDED.updated_at ELSE s.updated_at END,
          last_event_version = GREATEST(s.last_event_version, EXCLUDED.last_event_version)
      """;

  private static final String INSERT_ITEM =
      """
      INSERT INTO query.order_items (order_id, line_number, sku, quantity, unit_price)
      VALUES (:orderId, :lineNumber, :sku, :quantity, :unitPrice)
      ON CONFLICT DO NOTHING
      """;

  /** Cria a linha (ainda sem detalhes) ou aplica o status se o evento for mais novo. */
  private static final String UPSERT_STATUS =
      """
      INSERT INTO query.order_summaries AS s
          (order_id, status, cancellation_reason, updated_at, last_event_version)
      VALUES (:orderId, :status, :reason, :occurredAt, :version)
      ON CONFLICT (order_id) DO UPDATE SET
          status = EXCLUDED.status,
          cancellation_reason = EXCLUDED.cancellation_reason,
          updated_at = EXCLUDED.updated_at,
          last_event_version = EXCLUDED.last_event_version
      WHERE s.last_event_version < EXCLUDED.last_event_version
      """;

  private final JdbcClient jdbc;

  JdbcOrderProjectionStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void applyCreated(ProjectionEvent.OrderCreated event) {
    jdbc.sql(UPSERT_CREATED)
        .param("orderId", event.orderId().value())
        .param("customerId", event.customerId().value())
        .param("status", OrderStatus.CREATED.name())
        .param("currency", event.currency().getCurrencyCode())
        .param("total", event.total())
        .param("itemCount", event.items().size())
        .param("occurredAt", utc(event.occurredAt()))
        .param("version", event.aggregateVersion())
        .update();
    List<ProjectionEvent.Item> items = event.items();
    for (int i = 0; i < items.size(); i++) {
      ProjectionEvent.Item item = items.get(i);
      jdbc.sql(INSERT_ITEM)
          .param("orderId", event.orderId().value())
          .param("lineNumber", i)
          .param("sku", item.sku())
          .param("quantity", item.quantity())
          .param("unitPrice", item.unitPrice())
          .update();
    }
  }

  @Override
  public void applyStatusChange(ProjectionEvent.StatusChanged event) {
    jdbc.sql(UPSERT_STATUS)
        .param("orderId", event.orderId().value())
        .param("status", event.status().name())
        .param("reason", event.cancellationReason())
        .param("occurredAt", utc(event.occurredAt()))
        .param("version", event.aggregateVersion())
        .update();
  }

  private static OffsetDateTime utc(Instant instant) {
    return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }
}

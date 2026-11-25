package com.caiodassumpcao.orderflow.orders.adapter.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.caiodassumpcao.orderflow.orders.application.port.OrderProjectionStore;
import com.caiodassumpcao.orderflow.orders.application.projection.OrderProjectionService;
import com.caiodassumpcao.orderflow.orders.application.projection.ProjectionEvent;
import com.caiodassumpcao.orderflow.orders.domain.OrderStatus;
import com.caiodassumpcao.orderflow.shared.messaging.EventEnvelope;
import com.caiodassumpcao.orderflow.shared.messaging.PoisonMessageException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Tradução do contrato v1 dos eventos para a projeção (sem Kafka). */
class OrderProjectionListenerTest {

  private static final Instant AT = Instant.parse("2026-11-24T12:00:00Z");

  private final JsonMapper json = JsonMapper.builder().build();
  private final List<ProjectionEvent> applied = new ArrayList<>();
  private final OrderProjectionListener listener =
      new OrderProjectionListener(
          null,
          new OrderProjectionService(
              new OrderProjectionStore() {
                @Override
                public void applyCreated(ProjectionEvent.OrderCreated event) {
                  applied.add(event);
                }

                @Override
                public void applyStatusChange(ProjectionEvent.StatusChanged event) {
                  applied.add(event);
                }
              }),
          null);

  private EventEnvelope envelope(String type, int version, long aggregateVersion, String payload) {
    return new EventEnvelope(
        UUID.randomUUID(),
        type,
        version,
        UUID.randomUUID(),
        aggregateVersion,
        AT,
        null,
        json.readTree(payload));
  }

  @Test
  void translatesOrderCreatedWithItsDetails() {
    EventEnvelope e =
        envelope(
            "OrderCreated",
            1,
            0,
            """
            {"orderId":"x","customerId":"alice","currency":"BRL","total":"39.80",
             "items":[{"sku":"SKU-001","quantity":2,"unitPrice":"19.90"}]}
            """);

    listener.project(e);

    assertThat(applied)
        .singleElement()
        .isInstanceOfSatisfying(
            ProjectionEvent.OrderCreated.class,
            created -> {
              assertThat(created.orderId().value()).isEqualTo(e.aggregateId());
              assertThat(created.aggregateVersion()).isZero();
              assertThat(created.customerId().value()).isEqualTo("alice");
              assertThat(created.total()).isEqualByComparingTo("39.80");
              assertThat(created.items())
                  .singleElement()
                  .satisfies(item -> assertThat(item.unitPrice()).isEqualByComparingTo("19.90"));
            });
  }

  @Test
  void translatesStatusEventsKeepingTheReasonOnlyForCancellations() {
    listener.project(envelope("OrderConfirmed", 1, 1, "{\"orderId\":\"x\"}"));
    listener.project(envelope("OrderShipped", 1, 2, "{\"orderId\":\"x\",\"reason\":\"ignored\"}"));
    listener.project(
        envelope(
            "OrderCancelled",
            1,
            3,
            "{\"orderId\":\"x\",\"previousStatus\":\"CREATED\",\"reason\":\"changed my mind\"}"));
    listener.project(
        envelope("OrderCancelled", 1, 4, "{\"orderId\":\"x\",\"previousStatus\":\"CREATED\"}"));

    assertThat(applied)
        .extracting(e -> ((ProjectionEvent.StatusChanged) e))
        .extracting(
            ProjectionEvent.StatusChanged::status,
            ProjectionEvent.StatusChanged::aggregateVersion,
            ProjectionEvent.StatusChanged::cancellationReason)
        .containsExactly(
            tuple(OrderStatus.CONFIRMED, 1L, null),
            tuple(OrderStatus.SHIPPED, 2L, null),
            tuple(OrderStatus.CANCELLED, 3L, "changed my mind"),
            tuple(OrderStatus.CANCELLED, 4L, null));
  }

  @Test
  void ignoresEventTypesItDoesNotKnow() {
    listener.project(envelope("OrderGiftWrapped", 7, 1, "{}"));

    assertThat(applied).isEmpty();
  }

  @Test
  void treatsUnsupportedVersionsAndBrokenPayloadsAsPoison() {
    assertThatThrownBy(() -> listener.project(envelope("OrderConfirmed", 2, 1, "{}")))
        .isInstanceOf(PoisonMessageException.class)
        .hasMessageContaining("version 2");
    assertThatThrownBy(() ->
            listener.project(
                envelope(
                    "OrderCreated",
                    1,
                    0,
                    "{\"customerId\":\"alice\",\"currency\":\"BRL\",\"total\":\"1\"}")))
        .isInstanceOf(PoisonMessageException.class);
    assertThatThrownBy(() ->
            listener.project(
                envelope(
                    "OrderCreated",
                    1,
                    0,
                    """
                    {"customerId":"alice","currency":"XYZ","total":"1",
                     "items":[{"sku":"SKU-001","quantity":1,"unitPrice":"1"}]}
                    """)))
        .isInstanceOf(PoisonMessageException.class);
    assertThatThrownBy(() ->
            listener.project(
                envelope(
                    "OrderCreated",
                    1,
                    0,
                    """
                    {"customerId":"alice","currency":"BRL","total":"one",
                     "items":[{"sku":"SKU-001","quantity":1,"unitPrice":"1"}]}
                    """)))
        .isInstanceOf(PoisonMessageException.class);
    assertThatThrownBy(() ->
            listener.project(
                envelope(
                    "OrderCreated",
                    1,
                    0,
                    "{\"customerId\":\"alice\",\"currency\":\"BRL\",\"total\":\"1\","
                        + "\"items\":[]}")))
        .isInstanceOf(PoisonMessageException.class);
    assertThat(applied).isEmpty();
  }
}

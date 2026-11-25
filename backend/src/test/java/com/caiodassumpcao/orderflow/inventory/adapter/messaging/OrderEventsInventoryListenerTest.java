package com.caiodassumpcao.orderflow.inventory.adapter.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.caiodassumpcao.orderflow.inventory.application.StockReservationService;
import com.caiodassumpcao.orderflow.inventory.domain.ReservationLine;
import com.caiodassumpcao.orderflow.inventory.domain.Sku;
import com.caiodassumpcao.orderflow.shared.messaging.EventEnvelope;
import com.caiodassumpcao.orderflow.shared.messaging.PoisonMessageException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

/** Tradução do contrato dos eventos de pedidos para o inventário (sem Kafka). */
class OrderEventsInventoryListenerTest {

  private final JsonMapper json = JsonMapper.builder().build();
  private final StockReservationService reservations = mock(StockReservationService.class);
  private final OrderEventsInventoryListener listener =
      new OrderEventsInventoryListener(null, reservations, null);

  private EventEnvelope envelope(UUID orderId, String type, int version, String payload) {
    return new EventEnvelope(
        UUID.randomUUID(), type, version, orderId, 0, Instant.EPOCH, null, json.readTree(payload));
  }

  @Test
  @SuppressWarnings("unchecked")
  void reservesTheItemsOfACreatedOrder() {
    UUID orderId = UUID.randomUUID();

    listener.handle(
        envelope(
            orderId,
            "OrderCreated",
            1,
            """
            {"currency":"BRL","total":"39.80",
             "items":[{"sku":"SKU-001","quantity":2,"unitPrice":"19.90"}]}
            """));

    ArgumentCaptor<List<ReservationLine>> lines = ArgumentCaptor.forClass(List.class);
    verify(reservations).reserve(eq(orderId), eq(Currency.getInstance("BRL")), lines.capture());
    assertThat(lines.getValue())
        .containsExactly(new ReservationLine(new Sku("SKU-001"), 2, new BigDecimal("19.90")));
  }

  @Test
  void releasesOnCancellationAndFulfillsOnShipment() {
    UUID orderId = UUID.randomUUID();

    listener.handle(envelope(orderId, "OrderCancelled", 1, "{\"previousStatus\":\"CREATED\"}"));
    listener.handle(envelope(orderId, "OrderShipped", 1, "{}"));

    verify(reservations).release(orderId);
    verify(reservations).fulfill(orderId);
  }

  @Test
  void ignoresEventsItDoesNotUse() {
    listener.handle(envelope(UUID.randomUUID(), "OrderConfirmed", 1, "{}"));

    verifyNoInteractions(reservations);
  }

  @Test
  void treatsUnsupportedVersionsAndBrokenPayloadsAsPoison() {
    UUID orderId = UUID.randomUUID();

    assertThatThrownBy(() -> listener.handle(envelope(orderId, "OrderCancelled", 2, "{}")))
        .isInstanceOf(PoisonMessageException.class);
    assertThatThrownBy(() ->
            listener.handle(
                envelope(orderId, "OrderCreated", 1, "{\"currency\":\"BRL\",\"items\":[]}")))
        .isInstanceOf(PoisonMessageException.class);
    assertThatThrownBy(() ->
            listener.handle(
                envelope(
                    orderId,
                    "OrderCreated",
                    1,
                    """
                    {"currency":"BRL",
                     "items":[{"sku":"bad sku","quantity":1,"unitPrice":"1"}]}
                    """)))
        .isInstanceOf(PoisonMessageException.class);
    assertThatThrownBy(() ->
            listener.handle(
                envelope(
                    orderId,
                    "OrderCreated",
                    1,
                    """
                    {"currency":"BRL",
                     "items":[{"sku":"SKU-001","quantity":1,"unitPrice":"free"}]}
                    """)))
        .isInstanceOf(PoisonMessageException.class);
    assertThatThrownBy(
            () -> listener.handle(envelope(orderId, "OrderCreated", 1, "{\"items\":[{}]}")))
        .isInstanceOf(PoisonMessageException.class);
    verify(reservations, never()).reserve(any(), any(), anyList());
  }
}

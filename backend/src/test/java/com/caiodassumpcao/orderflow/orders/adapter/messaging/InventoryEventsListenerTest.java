package com.caiodassumpcao.orderflow.orders.adapter.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.caiodassumpcao.orderflow.orders.application.OrderSagaService;
import com.caiodassumpcao.orderflow.orders.domain.OrderId;
import com.caiodassumpcao.orderflow.shared.messaging.EventEnvelope;
import com.caiodassumpcao.orderflow.shared.messaging.PoisonMessageException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Tradução do contrato dos eventos do inventário para a saga de pedidos (sem Kafka). */
class InventoryEventsListenerTest {

  private final JsonMapper json = JsonMapper.builder().build();
  private final OrderSagaService saga = mock(OrderSagaService.class);
  private final InventoryEventsListener listener = new InventoryEventsListener(null, saga, null);

  private EventEnvelope envelope(UUID orderId, String type, int version, String payload) {
    return new EventEnvelope(
        UUID.randomUUID(), type, version, orderId, 0, Instant.EPOCH, null, json.readTree(payload));
  }

  @Test
  void confirmsOnStockReserved() {
    UUID orderId = UUID.randomUUID();

    listener.handle(envelope(orderId, "StockReserved", 1, "{\"items\":[]}"));

    verify(saga).onStockReserved(new OrderId(orderId));
  }

  @Test
  void cancelsWithAReadableReasonOnStockRejected() {
    UUID orderId = UUID.randomUUID();

    listener.handle(
        envelope(
            orderId,
            "StockRejected",
            1,
            "{\"reason\":\"OUT_OF_STOCK\",\"skus\":[\"SKU-001\",\"SKU-002\"]}"));

    verify(saga)
        .onStockRejected(new OrderId(orderId), "Stock rejected: OUT_OF_STOCK (SKU-001, SKU-002)");
  }

  @Test
  void writesTheReasonWithoutSkusWhenNoneAreGiven() {
    assertThat(
            InventoryEventsListener.rejectionReason(
                json.readTree("{\"reason\":\"PRICE_MISMATCH\"}")))
        .isEqualTo("Stock rejected: PRICE_MISMATCH");
  }

  @Test
  void ignoresUnknownTypesAndRejectsBrokenMessages() {
    UUID orderId = UUID.randomUUID();

    listener.handle(envelope(orderId, "StockAudited", 1, "{}"));
    verifyNoInteractions(saga);

    assertThatThrownBy(() -> listener.handle(envelope(orderId, "StockReserved", 2, "{}")))
        .isInstanceOf(PoisonMessageException.class);
    assertThatThrownBy(() -> listener.handle(envelope(orderId, "StockRejected", 1, "{}")))
        .isInstanceOf(PoisonMessageException.class);
  }
}

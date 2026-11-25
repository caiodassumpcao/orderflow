package com.caiodassumpcao.orderflow.inventory.adapter.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.caiodassumpcao.orderflow.inventory.application.StockReservationService;
import com.caiodassumpcao.orderflow.inventory.domain.ReservationLine;
import com.caiodassumpcao.orderflow.shared.messaging.DeadLetters;
import com.caiodassumpcao.orderflow.shared.messaging.EventContracts;
import com.caiodassumpcao.orderflow.shared.messaging.EventEnvelope;
import com.caiodassumpcao.orderflow.shared.messaging.EventEnvelopeCodec;
import com.caiodassumpcao.orderflow.shared.messaging.IdempotentEventProcessor;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

/** Consumidor: o inventário entende os exemplos publicados dos eventos de pedidos. */
class InventoryConsumerContractTest {

  private static final UUID ORDER = UUID.fromString("01a20f13-6a05-73f7-9b74-d102e42e88ab");

  private final StockReservationService reservations = mock(StockReservationService.class);
  private final OrderEventsInventoryListener listener =
      new OrderEventsInventoryListener(
          mock(IdempotentEventProcessor.class), reservations, mock(DeadLetters.class));
  private final EventEnvelopeCodec codec = new EventEnvelopeCodec(JsonMapper.builder().build());

  private EventEnvelope example(String type) {
    return codec.decode(EventContracts.example(type));
  }

  @Test
  @SuppressWarnings("unchecked")
  void reservesTheItemsOfOrderCreated() {
    listener.handle(example("OrderCreated"));

    ArgumentCaptor<List<ReservationLine>> lines = ArgumentCaptor.forClass(List.class);
    verify(reservations).reserve(eq(ORDER), eq(Currency.getInstance("BRL")), lines.capture());
    assertThat(lines.getValue())
        .extracting(line -> line.sku().value(), ReservationLine::quantity)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("SKU-001", 2),
            org.assertj.core.groups.Tuple.tuple("SKU-002", 1));
  }

  @Test
  void releasesOnOrderCancelledAndFulfillsOnOrderShipped() {
    listener.handle(example("OrderCancelled"));
    listener.handle(example("OrderShipped"));

    verify(reservations).release(ORDER);
    verify(reservations).fulfill(ORDER);
  }
}

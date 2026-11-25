package com.caiodassumpcao.orderflow.orders.adapter.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.caiodassumpcao.orderflow.orders.application.OrderSagaService;
import com.caiodassumpcao.orderflow.orders.application.projection.OrderProjectionService;
import com.caiodassumpcao.orderflow.orders.application.projection.ProjectionEvent;
import com.caiodassumpcao.orderflow.orders.domain.OrderId;
import com.caiodassumpcao.orderflow.orders.domain.OrderStatus;
import com.caiodassumpcao.orderflow.shared.messaging.DeadLetters;
import com.caiodassumpcao.orderflow.shared.messaging.EventContracts;
import com.caiodassumpcao.orderflow.shared.messaging.EventEnvelope;
import com.caiodassumpcao.orderflow.shared.messaging.EventEnvelopeCodec;
import com.caiodassumpcao.orderflow.shared.messaging.IdempotentEventProcessor;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

/** Consumidores do contexto de pedidos (saga e projector) entendem os exemplos publicados. */
class OrderConsumersContractTest {

  private static final OrderId ORDER =
      new OrderId(UUID.fromString("01a20f13-6a05-73f7-9b74-d102e42e88ab"));

  private final EventEnvelopeCodec codec = new EventEnvelopeCodec(JsonMapper.builder().build());

  private EventEnvelope example(String type) {
    return codec.decode(EventContracts.example(type));
  }

  @Test
  void sagaConfirmsOnStockReservedAndCancelsWithTheReasonOnStockRejected() {
    OrderSagaService saga = mock(OrderSagaService.class);
    InventoryEventsListener listener =
        new InventoryEventsListener(
            mock(IdempotentEventProcessor.class), saga, mock(DeadLetters.class));

    listener.handle(example("StockReserved"));
    listener.handle(example("StockRejected"));

    verify(saga).onStockReserved(ORDER);
    verify(saga).onStockRejected(ORDER, "Stock rejected: OUT_OF_STOCK (SKU-002)");
  }

  @Test
  void projectorAppliesEveryOrderEvent() {
    OrderProjectionService projection = mock(OrderProjectionService.class);
    OrderProjectionListener listener =
        new OrderProjectionListener(
            mock(IdempotentEventProcessor.class), projection, mock(DeadLetters.class));

    for (String type :
        new String[] {"OrderCreated", "OrderConfirmed", "OrderShipped", "OrderCancelled"}) {
      listener.project(example(type));
    }

    ArgumentCaptor<ProjectionEvent> applied = ArgumentCaptor.forClass(ProjectionEvent.class);
    verify(projection, times(4)).apply(applied.capture());
    assertThat(applied.getAllValues().getFirst())
        .isInstanceOfSatisfying(
            ProjectionEvent.OrderCreated.class,
            created -> {
              assertThat(created.total()).isEqualByComparingTo("139.80");
              assertThat(created.items()).hasSize(2);
            });
    assertThat(applied.getAllValues().subList(1, 4))
        .extracting(event -> ((ProjectionEvent.StatusChanged) event).status())
        .containsExactly(OrderStatus.CONFIRMED, OrderStatus.SHIPPED, OrderStatus.CANCELLED);
    assertThat(
            ((ProjectionEvent.StatusChanged) applied.getAllValues().getLast()).cancellationReason())
        .isEqualTo("Changed my mind");
  }
}

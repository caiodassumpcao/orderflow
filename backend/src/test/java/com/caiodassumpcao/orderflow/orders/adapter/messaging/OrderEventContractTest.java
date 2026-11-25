package com.caiodassumpcao.orderflow.orders.adapter.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.caiodassumpcao.orderflow.orders.domain.CustomerId;
import com.caiodassumpcao.orderflow.orders.domain.Money;
import com.caiodassumpcao.orderflow.orders.domain.Order;
import com.caiodassumpcao.orderflow.orders.domain.OrderEvent;
import com.caiodassumpcao.orderflow.orders.domain.OrderId;
import com.caiodassumpcao.orderflow.orders.domain.OrderLine;
import com.caiodassumpcao.orderflow.orders.domain.OrderStatus;
import com.caiodassumpcao.orderflow.orders.domain.Sku;
import com.caiodassumpcao.orderflow.shared.messaging.EventContracts;
import com.caiodassumpcao.orderflow.shared.messaging.OutboxMessage;
import com.caiodassumpcao.orderflow.shared.messaging.OutboxWriter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import tools.jackson.databind.json.JsonMapper;

/** Produtor: tudo que o contexto de pedidos grava na outbox respeita docs/events/schemas. */
class OrderEventContractTest {

  private static final Currency BRL = Currency.getInstance("BRL");
  private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");

  private final OutboxWriter writer = mock(OutboxWriter.class);
  private final OutboxOrderEventPublisher publisher =
      new OutboxOrderEventPublisher(writer, JsonMapper.builder().build());

  private static final List<OrderLine> LINES =
      List.of(
          new OrderLine(new Sku("SKU-001"), 2, new Money(new BigDecimal("19.90"), BRL)),
          new OrderLine(new Sku("SKU-002"), 1, new Money(new BigDecimal("100"), BRL)));
  private static final CustomerId CUSTOMER = new CustomerId("f81d4fae-7dec-11d0-a765-00a0c91e6bf6");

  /** Pedido já gravado no status dado (o agregado aceita uma mudança por carga). */
  private static Order stored(OrderStatus status) {
    return Order.rehydrate(
        new OrderId(UUID.randomUUID()), CUSTOMER, LINES, status, null, NOW, NOW, 1);
  }

  /** Um evento de cada tipo, pelos mesmos caminhos do código de produção. */
  private static List<OrderEvent> lifecycle() {
    List<OrderEvent> events = new ArrayList<>();
    events.addAll(Order.place(new OrderId(UUID.randomUUID()), CUSTOMER, LINES, NOW).pullEvents());

    Order confirmed = stored(OrderStatus.CREATED);
    confirmed.markStockReserved(NOW);
    confirmed.confirm(NOW);
    events.addAll(confirmed.pullEvents());

    Order shipped = stored(OrderStatus.CONFIRMED);
    shipped.ship(NOW);
    events.addAll(shipped.pullEvents());

    Order cancelledByCustomer = stored(OrderStatus.CREATED);
    cancelledByCustomer.cancel("Mudei de ideia", NOW);
    events.addAll(cancelledByCustomer.pullEvents());

    Order cancelledWithoutReason = stored(OrderStatus.STOCK_RESERVED);
    cancelledWithoutReason.cancel(null, NOW);
    events.addAll(cancelledWithoutReason.pullEvents());
    return events;
  }

  @Test
  void everyPublishedMessageMatchesTheEventContracts() {
    List<OrderEvent> events = lifecycle();

    publisher.append(events);

    ArgumentCaptor<OutboxMessage> written = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(writer, Mockito.times(events.size())).append(written.capture());
    verifyNoMoreInteractions(writer);
    assertThat(written.getAllValues())
        .extracting(OutboxMessage::eventType)
        .contains("OrderCreated", "OrderConfirmed", "OrderShipped", "OrderCancelled");
    for (OutboxMessage message : written.getAllValues()) {
      assertThat(EventContracts.messageViolations(EventContracts.published(message)))
          .as("%s v%d", message.eventType(), message.eventVersion())
          .isEmpty();
    }
  }
}

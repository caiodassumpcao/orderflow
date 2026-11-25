package com.caiodassumpcao.orderflow.orders.application;

import static com.caiodassumpcao.orderflow.orders.domain.OrderFixtures.CUSTOMER;
import static com.caiodassumpcao.orderflow.orders.domain.OrderFixtures.NOW;
import static com.caiodassumpcao.orderflow.orders.domain.OrderFixtures.line;
import static com.caiodassumpcao.orderflow.orders.domain.OrderFixtures.placedOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.caiodassumpcao.orderflow.orders.domain.Order;
import com.caiodassumpcao.orderflow.orders.domain.OrderEvent.OrderCancelled;
import com.caiodassumpcao.orderflow.orders.domain.OrderEvent.OrderConfirmed;
import com.caiodassumpcao.orderflow.orders.domain.OrderId;
import com.caiodassumpcao.orderflow.orders.domain.OrderStatus;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderSagaServiceTest {

  private InMemoryPorts ports;
  private OrderSagaService saga;

  @BeforeEach
  void setUp() {
    ports = InMemoryPorts.create();
    saga =
        new OrderSagaService(
            ports.orders, ports.outbox, ports.transactions, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private Order stored(OrderId id) {
    return ports.orders.findById(id).orElseThrow();
  }

  @Test
  void confirmsTheOrderWhenStockIsReserved() {
    Order order = placedOrder();
    ports.orders.seed(order);

    assertThat(saga.onStockReserved(order.id())).isTrue();

    assertThat(stored(order.id()).status()).isEqualTo(OrderStatus.CONFIRMED);
    assertThat(ports.outbox.events()).containsExactly(new OrderConfirmed(order.id(), 1, NOW));
  }

  @Test
  void cancelsTheOrderWithTheReasonWhenStockIsRejected() {
    Order order = placedOrder();
    ports.orders.seed(order);

    assertThat(saga.onStockRejected(order.id(), "Stock rejected: OUT_OF_STOCK (SKU-001)")).isTrue();

    Order cancelled = stored(order.id());
    assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(cancelled.cancellationReason()).contains("Stock rejected: OUT_OF_STOCK (SKU-001)");
    assertThat(ports.outbox.events())
        .containsExactly(
            new OrderCancelled(
                order.id(), 1, OrderStatus.CREATED, "Stock rejected: OUT_OF_STOCK (SKU-001)", NOW));
  }

  @Test
  void truncatesReasonsLongerThanTheOrderAccepts() {
    Order order = placedOrder();
    ports.orders.seed(order);

    saga.onStockRejected(order.id(), "x".repeat(500));

    assertThat(stored(order.id()).cancellationReason().orElseThrow())
        .hasSize(OrderSagaService.MAX_REASON_LENGTH)
        .endsWith("…");
  }

  @Test
  void ignoresAnswersForOrdersThatAlreadyMovedOn() {
    Order cancelled =
        Order.rehydrate(
            new OrderId(UUID.randomUUID()),
            CUSTOMER,
            List.of(line("SKU-001", 1, "1.00")),
            OrderStatus.CANCELLED,
            "customer changed their mind",
            NOW,
            NOW,
            1L);
    ports.orders.seed(cancelled);

    assertThat(saga.onStockReserved(cancelled.id())).isFalse();
    assertThat(saga.onStockRejected(cancelled.id(), "Stock rejected: OUT_OF_STOCK")).isFalse();

    assertThat(stored(cancelled.id()).cancellationReason()).contains("customer changed their mind");
    assertThat(ports.outbox.events()).isEmpty();
  }

  @Test
  void failsForUnknownOrdersSoTheMessageReachesTheDeadLetterTopic() {
    assertThatThrownBy(() -> saga.onStockReserved(new OrderId(UUID.randomUUID())))
        .isInstanceOf(OrderNotFoundException.class);
    assertThatThrownBy(() -> saga.onStockRejected(new OrderId(UUID.randomUUID()), "x"))
        .isInstanceOf(OrderNotFoundException.class);
  }
}

package com.caiodassumpcao.orderflow.orders.application;

import static com.caiodassumpcao.orderflow.orders.domain.OrderFixtures.BRL;
import static com.caiodassumpcao.orderflow.orders.domain.OrderFixtures.CUSTOMER;
import static com.caiodassumpcao.orderflow.orders.domain.OrderFixtures.NOW;
import static com.caiodassumpcao.orderflow.orders.domain.OrderFixtures.line;
import static com.caiodassumpcao.orderflow.orders.domain.OrderFixtures.placedOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.caiodassumpcao.orderflow.orders.application.port.IdempotencyKeyStore.StoredKey;
import com.caiodassumpcao.orderflow.orders.application.port.IdempotencyKeyTakenException;
import com.caiodassumpcao.orderflow.orders.domain.CustomerId;
import com.caiodassumpcao.orderflow.orders.domain.IllegalOrderStateException;
import com.caiodassumpcao.orderflow.orders.domain.InvalidOrderException;
import com.caiodassumpcao.orderflow.orders.domain.Order;
import com.caiodassumpcao.orderflow.orders.domain.OrderEvent.OrderCancelled;
import com.caiodassumpcao.orderflow.orders.domain.OrderEvent.OrderCreated;
import com.caiodassumpcao.orderflow.orders.domain.OrderEvent.OrderShipped;
import com.caiodassumpcao.orderflow.orders.domain.OrderId;
import com.caiodassumpcao.orderflow.orders.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OrderCommandServiceTest {

  private static final String KEY = "key-0001";

  private InMemoryPorts ports;
  private OrderCommandService service;

  @BeforeEach
  void setUp() {
    ports = InMemoryPorts.create();
    service =
        new OrderCommandService(
            ports.orders,
            ports.outbox,
            ports.keys,
            ports.transactions,
            Clock.fixed(NOW, ZoneOffset.UTC),
            () -> new OrderId(UUID.randomUUID()));
  }

  private static PlaceOrderCommand command(String key, PlaceOrderCommand.Item... items) {
    return new PlaceOrderCommand(CUSTOMER, key, "BRL", Arrays.asList(items));
  }

  private static PlaceOrderCommand.Item item(String sku, int quantity, String price) {
    return new PlaceOrderCommand.Item(sku, quantity, new BigDecimal(price));
  }

  @Nested
  class PlacingOrders {

    @Test
    void createsTheOrderStoresTheKeyAndAppendsOrderCreatedInOneTransaction() {
      PlaceOrderResult result =
          service.placeOrder(command(KEY, item("SKU-001", 2, "10.00"), item("SKU-002", 1, "5")));

      Order order = result.order();
      assertThat(result.replayed()).isFalse();
      assertThat(order.status()).isEqualTo(OrderStatus.CREATED);
      assertThat(order.total().amount()).isEqualByComparingTo("25.00");
      assertThat(order.createdAt()).isEqualTo(NOW);
      assertThat(ports.orders.findById(order.id())).isPresent();
      assertThat(ports.keys.find(CUSTOMER, KEY)).isPresent();
      assertThat(ports.outbox.events())
          .singleElement()
          .isInstanceOfSatisfying(
              OrderCreated.class, created -> assertThat(created.orderId()).isEqualTo(order.id()));
      assertThat(ports.transactions.committed()).isEqualTo(1);
    }

    @Test
    void replaysTheOriginalOrderWhenTheSameRequestIsRepeated() {
      PlaceOrderResult first = service.placeOrder(command(KEY, item("SKU-001", 2, "10.00")));

      PlaceOrderResult second = service.placeOrder(command(KEY, item("SKU-001", 2, "10")));

      assertThat(second.replayed()).isTrue();
      assertThat(second.order().id()).isEqualTo(first.order().id());
      assertThat(ports.orders.count()).isEqualTo(1);
      assertThat(ports.outbox.events()).hasSize(1);
    }

    @Test
    void rejectsReusingAKeyWithADifferentRequest() {
      service.placeOrder(command(KEY, item("SKU-001", 2, "10.00")));

      assertThatThrownBy(() -> service.placeOrder(command(KEY, item("SKU-001", 3, "10.00"))))
          .isInstanceOf(IdempotencyKeyReusedException.class);
      assertThat(ports.orders.count()).isEqualTo(1);
    }

    @Test
    void scopesKeysPerCustomer() {
      service.placeOrder(command(KEY, item("SKU-001", 1, "1.00")));
      PlaceOrderCommand otherCustomer =
          new PlaceOrderCommand(
              new CustomerId("customer-2"), KEY, "BRL", List.of(item("SKU-001", 1, "1.00")));

      PlaceOrderResult result = service.placeOrder(otherCustomer);

      assertThat(result.replayed()).isFalse();
      assertThat(ports.orders.count()).isEqualTo(2);
    }

    @Test
    void returnsTheWinningOrderWhenAConcurrentRequestRegisteredTheKeyFirst() {
      Order winner = placedOrder();
      ports.orders.seed(winner);
      String hash = OrderCommandService.fingerprint(BRL, List.of(line("SKU-001", 1, "1.00")));
      ports.keys.loseNextRaceTo(new StoredKey(hash, winner.id()));

      PlaceOrderResult result = service.placeOrder(command(KEY, item("SKU-001", 1, "1.00")));

      assertThat(result.replayed()).isTrue();
      assertThat(result.order().id()).isEqualTo(winner.id());
      assertThat(ports.orders.count()).isEqualTo(1);
      assertThat(ports.outbox.events()).isEmpty();
    }

    @Test
    void rejectsTheLoserOfARaceWhenItsContentDiffers() {
      Order winner = placedOrder();
      ports.orders.seed(winner);
      ports.keys.loseNextRaceTo(new StoredKey("another-hash", winner.id()));

      assertThatThrownBy(() -> service.placeOrder(command(KEY, item("SKU-001", 1, "1.00"))))
          .isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    void propagatesTheRaceWhenTheWinningKeyCannotBeFound() {
      ports.keys.loseNextRaceTo(null);

      assertThatThrownBy(() -> service.placeOrder(command(KEY, item("SKU-001", 1, "1.00"))))
          .isInstanceOf(IdempotencyKeyTakenException.class);
    }

    @Test
    void rollsBackOrderAndKeyWhenTheOutboxFails() {
      ports.outbox.failNextAppend();

      assertThatThrownBy(() -> service.placeOrder(command(KEY, item("SKU-001", 1, "1.00"))))
          .isInstanceOf(IllegalStateException.class);

      assertThat(ports.orders.count()).isZero();
      assertThat(ports.keys.count()).isZero();
      assertThat(ports.outbox.events()).isEmpty();
    }

    @Test
    void rejectsInvalidRequestsBeforeTouchingStorage() {
      assertThatThrownBy(() -> service.placeOrder(command(KEY)))
          .isInstanceOf(InvalidOrderException.class)
          .hasMessageContaining("at least one item");
      assertThatThrownBy(() ->
              service.placeOrder(
                  new PlaceOrderCommand(CUSTOMER, KEY, "XYZ", List.of(item("SKU-001", 1, "1.00")))))
          .isInstanceOf(InvalidOrderException.class)
          .hasMessageContaining("ISO 4217");
      assertThatThrownBy(() ->
              service.placeOrder(
                  new PlaceOrderCommand(CUSTOMER, KEY, null, List.of(item("SKU-001", 1, "1.00")))))
          .isInstanceOf(InvalidOrderException.class)
          .hasMessage("Currency is required");
      assertThatThrownBy(() -> service.placeOrder(command(KEY, (PlaceOrderCommand.Item) null)))
          .isInstanceOf(InvalidOrderException.class);

      assertThat(ports.orders.count()).isZero();
      assertThat(ports.transactions.committed()).isZero();
    }
  }

  @Nested
  class CancellingOrders {

    @Test
    void cancelsTheCustomersOrderAndAppendsOrderCancelled() {
      Order order = placedOrder();
      ports.orders.seed(order);

      Order cancelled = service.cancelOrder(order.id(), CUSTOMER, "changed my mind");

      assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
      assertThat(ports.orders.findById(order.id()).orElseThrow().status())
          .isEqualTo(OrderStatus.CANCELLED);
      assertThat(ports.outbox.events())
          .containsExactly(
              new OrderCancelled(order.id(), OrderStatus.CREATED, "changed my mind", NOW));
    }

    @Test
    void hidesOrdersOfOtherCustomers() {
      Order order = placedOrder();
      ports.orders.seed(order);

      assertThatThrownBy(
              () -> service.cancelOrder(order.id(), new CustomerId("intruder"), "mine now"))
          .isInstanceOf(OrderNotFoundException.class);
      assertThat(ports.orders.findById(order.id()).orElseThrow().status())
          .isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void reportsUnknownOrders() {
      OrderId unknown = new OrderId(UUID.randomUUID());

      assertThatThrownBy(() -> service.cancelOrder(unknown, CUSTOMER, null))
          .isInstanceOf(OrderNotFoundException.class)
          .hasMessage("Order " + unknown + " not found");
    }

    @Test
    void keepsTheOrderUnchangedWhenTheTransitionIsNotAllowed() {
      Order shipped =
          Order.rehydrate(
              new OrderId(UUID.randomUUID()),
              CUSTOMER,
              List.of(line("SKU-001", 1, "1.00")),
              OrderStatus.SHIPPED,
              null,
              NOW,
              NOW,
              2L);
      ports.orders.seed(shipped);

      assertThatThrownBy(() -> service.cancelOrder(shipped.id(), CUSTOMER, null))
          .isInstanceOf(IllegalOrderStateException.class);
      assertThat(ports.outbox.events()).isEmpty();
    }
  }

  @Nested
  class ShippingOrders {

    @Test
    void shipsAConfirmedOrderAndAppendsOrderShipped() {
      Order confirmed =
          Order.rehydrate(
              new OrderId(UUID.randomUUID()),
              CUSTOMER,
              List.of(line("SKU-001", 1, "1.00")),
              OrderStatus.CONFIRMED,
              null,
              NOW,
              NOW,
              2L);
      ports.orders.seed(confirmed);

      Order shipped = service.shipOrder(confirmed.id());

      assertThat(shipped.status()).isEqualTo(OrderStatus.SHIPPED);
      assertThat(ports.outbox.events()).containsExactly(new OrderShipped(confirmed.id(), NOW));
    }

    @Test
    void refusesToShipAnOrderThatIsNotConfirmed() {
      Order order = placedOrder();
      ports.orders.seed(order);

      assertThatThrownBy(() -> service.shipOrder(order.id()))
          .isInstanceOf(IllegalOrderStateException.class);
    }

    @Test
    void reportsUnknownOrders() {
      assertThatThrownBy(() -> service.shipOrder(new OrderId(UUID.randomUUID())))
          .isInstanceOf(OrderNotFoundException.class);
    }
  }

  @Nested
  class Fingerprint {

    @Test
    void isStableForEquivalentRequests() {
      assertThat(OrderCommandService.fingerprint(BRL, List.of(line("SKU-001", 2, "10"))))
          .isEqualTo(OrderCommandService.fingerprint(BRL, List.of(line("SKU-001", 2, "10.00"))))
          .hasSize(64);
    }

    @Test
    void changesWithQuantityPriceSkuOrder() {
      String base =
          OrderCommandService.fingerprint(
              BRL, List.of(line("SKU-001", 2, "10.00"), line("SKU-002", 1, "1.00")));

      assertThat(base)
          .isNotEqualTo(
              OrderCommandService.fingerprint(
                  BRL, List.of(line("SKU-001", 3, "10.00"), line("SKU-002", 1, "1.00"))))
          .isNotEqualTo(
              OrderCommandService.fingerprint(
                  BRL, List.of(line("SKU-001", 2, "10.01"), line("SKU-002", 1, "1.00"))))
          .isNotEqualTo(
              OrderCommandService.fingerprint(
                  BRL, List.of(line("SKU-002", 1, "1.00"), line("SKU-001", 2, "10.00"))));
    }
  }
}

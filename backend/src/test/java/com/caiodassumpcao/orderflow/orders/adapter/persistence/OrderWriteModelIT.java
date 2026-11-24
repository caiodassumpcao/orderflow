package com.caiodassumpcao.orderflow.orders.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.caiodassumpcao.orderflow.TestcontainersConfiguration;
import com.caiodassumpcao.orderflow.orders.application.OrderCommandService;
import com.caiodassumpcao.orderflow.orders.application.PlaceOrderCommand;
import com.caiodassumpcao.orderflow.orders.application.PlaceOrderResult;
import com.caiodassumpcao.orderflow.orders.application.port.ConcurrentOrderUpdateException;
import com.caiodassumpcao.orderflow.orders.application.port.OrderRepository;
import com.caiodassumpcao.orderflow.orders.application.port.UnitOfWork;
import com.caiodassumpcao.orderflow.orders.domain.CustomerId;
import com.caiodassumpcao.orderflow.orders.domain.Money;
import com.caiodassumpcao.orderflow.orders.domain.Order;
import com.caiodassumpcao.orderflow.orders.domain.OrderId;
import com.caiodassumpcao.orderflow.orders.domain.OrderLine;
import com.caiodassumpcao.orderflow.orders.domain.OrderStatus;
import com.caiodassumpcao.orderflow.orders.domain.Sku;
import com.caiodassumpcao.orderflow.shared.id.UuidV7;
import com.caiodassumpcao.orderflow.shared.messaging.OutboxMessage;
import com.caiodassumpcao.orderflow.shared.messaging.OutboxWriter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Modelo de escrita com PostgreSQL real: transação única, idempotência, versão e outbox. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OrderWriteModelIT {

  @Autowired private OrderCommandService service;
  @Autowired private OrderRepository orders;
  @Autowired private UnitOfWork unitOfWork;
  @Autowired private OutboxWriter outboxWriter;
  @Autowired private JdbcClient jdbc;
  @Autowired private JsonMapper json;

  private static CustomerId newCustomer() {
    return new CustomerId("it-" + UUID.randomUUID());
  }

  private static PlaceOrderCommand command(CustomerId customer, String key) {
    return new PlaceOrderCommand(
        customer,
        key,
        "BRL",
        List.of(
            new PlaceOrderCommand.Item("SKU-001", 2, new BigDecimal("19.90")),
            new PlaceOrderCommand.Item("SKU-002", 1, new BigDecimal("100"))));
  }

  @Test
  void persistsOrderLinesKeyAndOrderCreatedTogether() {
    CustomerId customer = newCustomer();

    Order order = service.placeOrder(command(customer, "key-persist-1")).order();

    Map<String, Object> row =
        jdbc.sql("SELECT status, currency, total_amount, version FROM command.orders WHERE id = ?")
            .param(order.id().value())
            .query()
            .singleRow();
    assertThat(row)
        .containsEntry("status", "CREATED")
        .containsEntry("currency", "BRL")
        .containsEntry("version", 0L);
    assertThat((BigDecimal) row.get("total_amount")).isEqualByComparingTo("139.80");
    assertThat(
            jdbc.sql("SELECT sku FROM command.order_lines WHERE order_id = ? ORDER BY line_number")
                .param(order.id().value())
                .query(String.class)
                .list())
        .containsExactly("SKU-001", "SKU-002");
    assertThat(keyCount(customer)).isEqualTo(1);
    assertThat(outboxTypes(order.id())).containsExactly("OrderCreated");
  }

  @Test
  void writesTheEventEnvelopeAndVersionedPayloadToTheOutbox() {
    CustomerId customer = newCustomer();
    Order order = service.placeOrder(command(customer, "key-outbox-1")).order();

    Map<String, Object> row =
        jdbc.sql(
                """
                SELECT id, aggregate_type, event_version, payload::text AS payload,
                       occurred_at, published_at
                  FROM command.outbox WHERE aggregate_id = ?
                """)
            .param(order.id().value())
            .query()
            .singleRow();

    assertThat(row)
        .containsEntry("aggregate_type", "Order")
        .containsEntry("event_version", 1)
        .containsEntry("published_at", null);
    assertThat(((UUID) row.get("id")).version()).isEqualTo(7);
    JsonNode payload = json.readTree((String) row.get("payload"));
    assertThat(payload.get("orderId").asString()).isEqualTo(order.id().toString());
    assertThat(payload.get("customerId").asString()).isEqualTo(customer.value());
    assertThat(payload.get("currency").asString()).isEqualTo("BRL");
    assertThat(payload.get("total").asString()).isEqualTo("139.80");
    assertThat(payload.get("items")).hasSize(2);
    assertThat(payload.get("items").get(0).get("unitPrice").asString()).isEqualTo("19.90");
  }

  @Test
  void replayingTheSameRequestDoesNotDuplicateAnything() {
    CustomerId customer = newCustomer();
    Order first = service.placeOrder(command(customer, "key-replay-1")).order();

    PlaceOrderResult replay = service.placeOrder(command(customer, "key-replay-1"));

    assertThat(replay.replayed()).isTrue();
    assertThat(replay.order().id()).isEqualTo(first.id());
    assertThat(orderCount(customer)).isEqualTo(1);
    assertThat(outboxTypes(first.id())).hasSize(1);
  }

  @Test
  void concurrentRequestsWithTheSameKeyCreateExactlyOneOrder() throws Exception {
    CustomerId customer = newCustomer();
    int requests = 6;
    CountDownLatch start = new CountDownLatch(1);
    Callable<PlaceOrderResult> place =
        () -> {
          start.await();
          return service.placeOrder(command(customer, "key-race-1"));
        };

    try (ExecutorService pool = Executors.newFixedThreadPool(requests)) {
      List<Future<PlaceOrderResult>> futures =
          IntStream.range(0, requests).mapToObj(i -> pool.submit(place)).toList();
      start.countDown();

      List<PlaceOrderResult> results = new ArrayList<>();
      for (Future<PlaceOrderResult> future : futures) {
        results.add(future.get());
      }

      assertThat(results)
          .extracting(r -> r.order().id())
          .containsOnly(results.getFirst().order().id());
      assertThat(results).filteredOn(r -> !r.replayed()).hasSize(1);
    }
    assertThat(orderCount(customer)).isEqualTo(1);
    assertThat(keyCount(customer)).isEqualTo(1);
  }

  @Test
  void cancellingUpdatesStateBumpsTheVersionAndAppendsOrderCancelled() {
    CustomerId customer = newCustomer();
    Order order = service.placeOrder(command(customer, "key-cancel-1")).order();

    service.cancelOrder(order.id(), customer, "changed my mind");

    Order stored = unitOfWork.inTransaction(() -> orders.findById(order.id()).orElseThrow());
    assertThat(stored.status()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(stored.cancellationReason()).contains("changed my mind");
    assertThat(stored.version()).contains(1L);
    assertThat(outboxTypes(order.id())).containsExactly("OrderCreated", "OrderCancelled");
  }

  @Test
  void numbersOutboxRowsInWriteOrder() {
    CustomerId customer = newCustomer();
    Order order = service.placeOrder(command(customer, "key-position-1")).order();
    service.cancelOrder(order.id(), customer, null);

    List<Long> positions =
        jdbc.sql("SELECT position FROM command.outbox WHERE aggregate_id = ? ORDER BY position")
            .param(order.id().value())
            .query(Long.class)
            .list();

    assertThat(positions).hasSize(2).isSorted().doesNotHaveDuplicates();
    assertThat(outboxTypes(order.id())).containsExactly("OrderCreated", "OrderCancelled");
  }

  @Test
  void rejectsAStaleUpdateWithAVersionConflict() {
    CustomerId customer = newCustomer();
    OrderId id = service.placeOrder(command(customer, "key-stale-1")).order().id();
    Order stale = unitOfWork.inTransaction(() -> orders.findById(id).orElseThrow());

    service.cancelOrder(id, customer, null);
    stale.cancel("too late", Instant.now());

    assertThatThrownBy(() -> unitOfWork.inTransaction(() -> save(stale)))
        .isInstanceOf(ConcurrentOrderUpdateException.class);
  }

  @Test
  void jpaAndJdbcWritesShareOneTransaction() {
    CustomerId customer = newCustomer();
    Order order = OrderAssembler.newOrder(customer);
    OutboxMessage message =
        new OutboxMessage(
            UuidV7.generate(), "Order", order.id().value(), "Probe", 1, "{}", null, Instant.now());

    assertThatThrownBy(() ->
            unitOfWork.inTransaction(() -> {
              orders.save(order);
              outboxWriter.append(message);
              throw new IllegalStateException("boom after both writes");
            }))
        .hasMessage("boom after both writes");

    assertThat(orderCount(customer)).isZero();
    assertThat(outboxTypes(order.id())).isEmpty();
  }

  @Test
  void writesOutsideATransactionAreRefused() {
    Order order = OrderAssembler.newOrder(newCustomer());

    assertThatThrownBy(() -> orders.save(order))
        .isInstanceOf(IllegalTransactionStateException.class);
  }

  private Void save(Order order) {
    orders.save(order);
    return null;
  }

  private long orderCount(CustomerId customer) {
    return jdbc.sql("SELECT count(*) FROM command.orders WHERE customer_id = ?")
        .param(customer.value())
        .query(Long.class)
        .single();
  }

  private long keyCount(CustomerId customer) {
    return jdbc.sql("SELECT count(*) FROM command.idempotency_keys WHERE customer_id = ?")
        .param(customer.value())
        .query(Long.class)
        .single();
  }

  private List<String> outboxTypes(OrderId id) {
    return jdbc.sql(
            "SELECT event_type FROM command.outbox WHERE aggregate_id = ? ORDER BY position")
        .param(id.value())
        .query(String.class)
        .list();
  }

  /** Monta pedidos de teste sem passar pelo serviço. */
  private static final class OrderAssembler {

    static Order newOrder(CustomerId customer) {
      return Order.place(
          new OrderId(UuidV7.generate()),
          customer,
          List.of(
              new OrderLine(
                  new Sku("SKU-001"), 1, new Money(BigDecimal.TEN, Currency.getInstance("BRL")))),
          Instant.now());
    }
  }
}

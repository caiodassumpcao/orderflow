package com.caiodassumpcao.orderflow.orders.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.caiodassumpcao.orderflow.TestcontainersConfiguration;
import com.caiodassumpcao.orderflow.orders.application.port.OrderProjectionStore;
import com.caiodassumpcao.orderflow.orders.application.port.OrderReadModel;
import com.caiodassumpcao.orderflow.orders.application.port.UnitOfWork;
import com.caiodassumpcao.orderflow.orders.application.projection.ProjectionEvent;
import com.caiodassumpcao.orderflow.orders.application.query.OrderDetail;
import com.caiodassumpcao.orderflow.orders.application.query.OrderSummary;
import com.caiodassumpcao.orderflow.orders.application.query.PageCursor;
import com.caiodassumpcao.orderflow.orders.domain.CustomerId;
import com.caiodassumpcao.orderflow.orders.domain.OrderId;
import com.caiodassumpcao.orderflow.orders.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Projeção e consultas com PostgreSQL real: ordem de chegada qualquer e paginação keyset. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OrderReadModelIT {

  private static final Instant T0 = Instant.parse("2026-11-24T12:00:00Z");

  @Autowired private OrderProjectionStore store;
  @Autowired private OrderReadModel readModel;
  @Autowired private UnitOfWork unitOfWork;

  private static CustomerId newCustomer() {
    return new CustomerId("rm-" + UUID.randomUUID());
  }

  private static ProjectionEvent.OrderCreated created(OrderId id, CustomerId customer, Instant at) {
    return new ProjectionEvent.OrderCreated(
        id,
        0,
        at,
        customer,
        Currency.getInstance("BRL"),
        new BigDecimal("39.80"),
        List.of(
            new ProjectionEvent.Item("SKU-001", 1, new BigDecimal("19.90")),
            new ProjectionEvent.Item("SKU-002", 1, new BigDecimal("19.90"))));
  }

  private static ProjectionEvent.StatusChanged status(
      OrderId id, long version, OrderStatus status, String reason) {
    return new ProjectionEvent.StatusChanged(id, version, T0.plusSeconds(version), status, reason);
  }

  private void apply(ProjectionEvent event) {
    unitOfWork.inTransaction(() -> {
      switch (event) {
        case ProjectionEvent.OrderCreated c -> store.applyCreated(c);
        case ProjectionEvent.StatusChanged s -> store.applyStatusChange(s);
      }
      return null;
    });
  }

  @Test
  void keepsAStatusThatArrivedBeforeTheOrderWasCreated() {
    CustomerId customer = newCustomer();
    OrderId id = new OrderId(UUID.randomUUID());

    apply(status(id, 1, OrderStatus.CANCELLED, "changed my mind"));
    assertThat(readModel.findOrder(id, customer)).isEmpty();
    assertThat(readModel.findOrders(customer, Optional.empty(), Optional.empty(), 10)).isEmpty();

    apply(created(id, customer, T0));

    OrderDetail detail = readModel.findOrder(id, customer).orElseThrow();
    assertThat(detail.summary().status()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(detail.cancellationReason()).isEqualTo("changed my mind");
    assertThat(detail.summary().createdAt()).isEqualTo(T0);
    assertThat(detail.summary().itemCount()).isEqualTo(2);
    assertThat(detail.items())
        .extracting(OrderDetail.Item::sku)
        .containsExactly("SKU-001", "SKU-002");
  }

  @Test
  void ignoresAStatusOlderThanTheOneAlreadyApplied() {
    CustomerId customer = newCustomer();
    OrderId id = new OrderId(UUID.randomUUID());
    apply(created(id, customer, T0));

    apply(status(id, 2, OrderStatus.SHIPPED, null));
    apply(status(id, 1, OrderStatus.CONFIRMED, null));

    OrderSummary summary = readModel.findOrder(id, customer).orElseThrow().summary();
    assertThat(summary.status()).isEqualTo(OrderStatus.SHIPPED);
    assertThat(summary.updatedAt()).isEqualTo(T0.plusSeconds(2));
  }

  @Test
  void toleratesTheSameOrderCreatedTwice() {
    CustomerId customer = newCustomer();
    OrderId id = new OrderId(UUID.randomUUID());

    apply(created(id, customer, T0));
    apply(created(id, customer, T0));

    assertThat(readModel.findOrder(id, customer).orElseThrow().items()).hasSize(2);
    assertThat(readModel.findOrders(customer, Optional.empty(), Optional.empty(), 10)).hasSize(1);
  }

  @Test
  void showsOrdersOnlyToTheirCustomer() {
    CustomerId alice = newCustomer();
    OrderId id = new OrderId(UUID.randomUUID());
    apply(created(id, alice, T0));

    CustomerId mallory = newCustomer();
    assertThat(readModel.findOrder(id, mallory)).isEmpty();
    assertThat(readModel.findOrders(mallory, Optional.empty(), Optional.empty(), 10)).isEmpty();
  }

  @Test
  void pagesThroughEveryOrderExactlyOnceEvenWithIdenticalCreationTimes() {
    CustomerId customer = newCustomer();
    List<OrderId> ids = new ArrayList<>();
    for (int i = 0; i < 7; i++) {
      OrderId id = new OrderId(UUID.randomUUID());
      ids.add(id);
      // Pares de pedidos com o mesmo instante: o desempate é pelo id.
      apply(created(id, customer, T0.plusSeconds(i / 2)));
    }
    apply(status(ids.get(3), 1, OrderStatus.CANCELLED, null));

    List<OrderSummary> seen = new ArrayList<>();
    Optional<PageCursor> cursor = Optional.empty();
    do {
      List<OrderSummary> page = readModel.findOrders(customer, Optional.empty(), cursor, 3);
      seen.addAll(page);
      cursor =
          page.size() < 3
              ? Optional.empty()
              : Optional.of(new PageCursor(page.getLast().createdAt(), page.getLast().id()));
    } while (cursor.isPresent());

    assertThat(seen).extracting(OrderSummary::id).containsExactlyInAnyOrderElementsOf(ids);
    // UUID.compareTo compara como long com sinal; o PostgreSQL ordena os bytes sem sinal, que é a
    // ordem da forma textual.
    assertThat(seen)
        .isSortedAccordingTo(
            Comparator.comparing(OrderSummary::createdAt)
                .thenComparing(s -> s.id().value().toString())
                .reversed());
    assertThat(
            readModel.findOrders(
                customer, Optional.of(OrderStatus.CANCELLED), Optional.empty(), 10))
        .extracting(OrderSummary::id)
        .containsExactly(ids.get(3));
  }
}

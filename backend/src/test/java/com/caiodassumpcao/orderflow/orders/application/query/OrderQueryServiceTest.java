package com.caiodassumpcao.orderflow.orders.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.caiodassumpcao.orderflow.orders.application.OrderNotFoundException;
import com.caiodassumpcao.orderflow.orders.application.port.OrderReadModel;
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
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class OrderQueryServiceTest {

  private static final CustomerId ALICE = new CustomerId("alice");
  private static final Instant T0 = Instant.parse("2026-11-24T12:00:00Z");

  /** Modelo de leitura em memória com a mesma ordenação e filtros do adapter. */
  private static final class InMemoryReadModel implements OrderReadModel {

    private static final Comparator<OrderSummary> NEWEST_FIRST =
        Comparator.comparing(OrderSummary::createdAt)
            .thenComparing(s -> s.id().value().toString())
            .reversed();

    private final List<OrderDetail> rows = new ArrayList<>();
    private int lastLimit;

    /** Guarda o pedido como sendo do cliente informado. */
    void add(CustomerId owner, OrderSummary summary) {
      OrderSummary owned =
          new OrderSummary(
              summary.id(),
              owner,
              summary.status(),
              summary.currency(),
              summary.total(),
              summary.itemCount(),
              summary.createdAt(),
              summary.updatedAt());
      rows.add(new OrderDetail(owned, List.of(), null));
    }

    @Override
    public List<OrderSummary> findOrders(
        Optional<CustomerId> customerId,
        Optional<OrderStatus> status,
        Optional<PageCursor> after,
        int limit) {
      lastLimit = limit;
      return rows.stream()
          .map(OrderDetail::summary)
          .filter(s -> customerId.map(c -> c.equals(s.customerId())).orElse(true))
          .filter(s -> status.map(st -> st == s.status()).orElse(true))
          .filter(s -> after.map(c -> isAfter(s, c)).orElse(true))
          .sorted(NEWEST_FIRST)
          .limit(limit)
          .toList();
    }

    @Override
    public Optional<OrderDetail> findOrder(OrderId orderId, Optional<CustomerId> customerId) {
      return rows.stream()
          .filter(d -> d.summary().id().equals(orderId))
          .filter(d -> customerId.map(c -> c.equals(d.summary().customerId())).orElse(true))
          .findFirst();
    }

    private static boolean isAfter(OrderSummary summary, PageCursor cursor) {
      OrderSummary cursorRow =
          new OrderSummary(
              cursor.orderId(),
              summary.customerId(),
              summary.status(),
              summary.currency(),
              summary.total(),
              0,
              cursor.createdAt(),
              cursor.createdAt());
      return NEWEST_FIRST.compare(summary, cursorRow) > 0;
    }
  }

  private static OrderSummary summary(int minute, OrderStatus status) {
    return new OrderSummary(
        new OrderId(UUID.randomUUID()),
        ALICE,
        status,
        Currency.getInstance("BRL"),
        BigDecimal.TEN,
        1,
        T0.plusSeconds(60L * minute),
        T0.plusSeconds(60L * minute));
  }

  private final InMemoryReadModel readModel = new InMemoryReadModel();
  private final OrderQueryService service = new OrderQueryService(readModel);

  @Test
  void walksAllOrdersNewestFirstPageByPage() {
    IntStream.range(0, 5).forEach(i -> readModel.add(ALICE, summary(i, OrderStatus.CREATED)));

    OrderPage first =
        service.listOrders(new OrderListQuery(ALICE, Optional.empty(), 2, Optional.empty()));
    OrderPage second =
        service.listOrders(new OrderListQuery(ALICE, Optional.empty(), 2, first.next()));
    OrderPage last =
        service.listOrders(new OrderListQuery(ALICE, Optional.empty(), 2, second.next()));

    assertThat(first.items())
        .extracting(OrderSummary::createdAt)
        .containsExactly(T0.plusSeconds(240), T0.plusSeconds(180));
    assertThat(second.items())
        .extracting(OrderSummary::createdAt)
        .containsExactly(T0.plusSeconds(120), T0.plusSeconds(60));
    assertThat(last.items()).extracting(OrderSummary::createdAt).containsExactly(T0);
    assertThat(last.next()).isEmpty();
    assertThat(first.next())
        .contains(new PageCursor(T0.plusSeconds(180), first.items().get(1).id()));
  }

  @Test
  void asksTheReadModelForOneExtraRowToDetectTheNextPage() {
    readModel.add(ALICE, summary(0, OrderStatus.CREATED));
    readModel.add(ALICE, summary(1, OrderStatus.CREATED));

    OrderPage page =
        service.listOrders(new OrderListQuery(ALICE, Optional.empty(), 2, Optional.empty()));

    assertThat(readModel.lastLimit).isEqualTo(3);
    assertThat(page.items()).hasSize(2);
    assertThat(page.next()).isEmpty();
  }

  @Test
  void filtersByStatusAndOwner() {
    readModel.add(ALICE, summary(0, OrderStatus.CREATED));
    readModel.add(ALICE, summary(1, OrderStatus.CANCELLED));
    readModel.add(new CustomerId("bob"), summary(2, OrderStatus.CANCELLED));

    OrderPage page =
        service.listOrders(
            new OrderListQuery(ALICE, Optional.of(OrderStatus.CANCELLED), 10, Optional.empty()));

    assertThat(page.items())
        .singleElement()
        .satisfies(s -> assertThat(s.status()).isEqualTo(OrderStatus.CANCELLED));
  }

  @Test
  void returnsTheDetailOfTheCustomersOwnOrder() {
    OrderSummary own = summary(0, OrderStatus.CREATED);
    readModel.add(ALICE, own);

    assertThat(service.getOrder(own.id(), ALICE).summary()).isEqualTo(own);
  }

  @Test
  void hidesUnknownOrOtherCustomersOrders() {
    OrderSummary bobs = summary(0, OrderStatus.CREATED);
    readModel.add(new CustomerId("bob"), bobs);

    assertThatThrownBy(() -> service.getOrder(bobs.id(), ALICE))
        .isInstanceOf(OrderNotFoundException.class);
    assertThatThrownBy(() -> service.getOrder(new OrderId(UUID.randomUUID()), ALICE))
        .isInstanceOf(OrderNotFoundException.class);
  }

  @Test
  void letsTheAdminListEveryCustomerOrFilterByOne() {
    readModel.add(ALICE, summary(0, OrderStatus.CREATED));
    readModel.add(new CustomerId("bob"), summary(1, OrderStatus.CREATED));

    OrderPage all =
        service.listAllOrders(
            new AdminOrderListQuery(Optional.empty(), Optional.empty(), 10, Optional.empty()));
    OrderPage bobs =
        service.listAllOrders(
            new AdminOrderListQuery(
                Optional.of(new CustomerId("bob")), Optional.empty(), 10, Optional.empty()));

    assertThat(all.items()).hasSize(2);
    assertThat(bobs.items())
        .singleElement()
        .satisfies(s -> assertThat(s.customerId()).isEqualTo(new CustomerId("bob")));
  }

  @Test
  void letsTheAdminReadAnyOrder() {
    OrderSummary bobs = summary(0, OrderStatus.CREATED);
    readModel.add(new CustomerId("bob"), bobs);

    assertThat(service.getAnyOrder(bobs.id()).summary().customerId())
        .isEqualTo(new CustomerId("bob"));
    assertThatThrownBy(() -> service.getAnyOrder(new OrderId(UUID.randomUUID())))
        .isInstanceOf(OrderNotFoundException.class);
    assertThatThrownBy(
            () -> new AdminOrderListQuery(Optional.empty(), Optional.empty(), 0, Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsPageSizesOutsideTheAllowedRange() {
    assertThatThrownBy(() -> new OrderListQuery(ALICE, Optional.empty(), 0, Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() ->
            new OrderListQuery(
                ALICE, Optional.empty(), OrderListQuery.MAX_LIMIT + 1, Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void computesItemTotals() {
    assertThat(new OrderDetail.Item("SKU-001", 3, new BigDecimal("19.90")).total())
        .isEqualByComparingTo("59.70");
  }
}

package com.caiodassumpcao.orderflow.orders.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.caiodassumpcao.orderflow.orders.application.port.OrderStatistics;
import com.caiodassumpcao.orderflow.orders.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardQueryServiceTest {

  private static final Currency BRL = Currency.getInstance("BRL");
  private static final Currency USD = Currency.getInstance("USD");
  private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

  /** 01:30 em UTC do dia 25 ainda é dia 24 em São Paulo (UTC-3). */
  private static final Instant NOW = Instant.parse("2026-11-25T01:30:00Z");

  private final List<OrderStatistics.StatusTotal> totals = new ArrayList<>();
  private final List<OrderStatistics.DailyOrders> days = new ArrayList<>();
  private Instant requestedFrom;
  private ZoneId requestedZone;

  private final OrderStatistics statistics =
      new OrderStatistics() {
        @Override
        public List<StatusTotal> totalsByStatus() {
          return totals;
        }

        @Override
        public List<DailyOrders> dailyOrders(Instant from, ZoneId zone) {
          requestedFrom = from;
          requestedZone = zone;
          return days;
        }
      };

  private final DashboardQueryService service =
      new DashboardQueryService(statistics, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void countsEveryStatusAndSumsOnlyConfirmedRevenuePerCurrency() {
    totals.add(
        new OrderStatistics.StatusTotal(OrderStatus.CONFIRMED, BRL, 2, new BigDecimal("30.00")));
    totals.add(
        new OrderStatistics.StatusTotal(OrderStatus.SHIPPED, BRL, 1, new BigDecimal("12.50")));
    totals.add(
        new OrderStatistics.StatusTotal(OrderStatus.SHIPPED, USD, 1, new BigDecimal("5.00")));
    totals.add(
        new OrderStatistics.StatusTotal(OrderStatus.CANCELLED, BRL, 4, new BigDecimal("99.00")));
    totals.add(
        new OrderStatistics.StatusTotal(OrderStatus.CREATED, BRL, 1, new BigDecimal("1.00")));

    DashboardSummary summary = service.summary(SAO_PAULO, 7);

    assertThat(summary.generatedAt()).isEqualTo(NOW);
    assertThat(summary.totalOrders()).isEqualTo(9);
    assertThat(summary.ordersByStatus())
        .containsEntry(OrderStatus.CREATED, 1L)
        .containsEntry(OrderStatus.STOCK_RESERVED, 0L)
        .containsEntry(OrderStatus.CONFIRMED, 2L)
        .containsEntry(OrderStatus.SHIPPED, 2L)
        .containsEntry(OrderStatus.CANCELLED, 4L);
    assertThat(summary.confirmedRevenue())
        .extracting(r -> r.currency().getCurrencyCode(), r -> r.amount().toPlainString())
        .containsExactly(tuple("BRL", "42.50"), tuple("USD", "5.00"));
  }

  @Test
  void buildsADailySeriesInTheRequestedZoneWithoutGaps() {
    days.add(new OrderStatistics.DailyOrders(LocalDate.parse("2026-11-20"), 3, 1));
    days.add(new OrderStatistics.DailyOrders(LocalDate.parse("2026-11-24"), 2, 0));

    DashboardSummary summary = service.summary(SAO_PAULO, 5);

    assertThat(requestedZone).isEqualTo(SAO_PAULO);
    assertThat(requestedFrom).isEqualTo(Instant.parse("2026-11-20T03:00:00Z"));
    assertThat(summary.daily())
        .extracting(d -> d.date().toString(), OrderStatistics.DailyOrders::orders)
        .containsExactly(
            tuple("2026-11-20", 3L),
            tuple("2026-11-21", 0L),
            tuple("2026-11-22", 0L),
            tuple("2026-11-23", 0L),
            tuple("2026-11-24", 2L));
  }

  @Test
  void startsEmptyWithZeroedCounters() {
    DashboardSummary summary = service.summary(ZoneOffset.UTC, 1);

    assertThat(summary.totalOrders()).isZero();
    assertThat(summary.ordersByStatus()).hasSize(OrderStatus.values().length).containsValue(0L);
    assertThat(summary.confirmedRevenue()).isEmpty();
    assertThat(summary.daily())
        .singleElement()
        .satisfies(d -> {
          assertThat(d.date()).isEqualTo(LocalDate.parse("2026-11-25"));
          assertThat(d.orders()).isZero();
        });
  }

  @Test
  void refusesPeriodsOutsideTheRange() {
    assertThatThrownBy(() -> service.summary(ZoneOffset.UTC, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.summary(ZoneOffset.UTC, DashboardQueryService.MAX_DAYS + 1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}

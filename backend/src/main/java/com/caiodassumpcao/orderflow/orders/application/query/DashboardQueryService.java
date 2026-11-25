package com.caiodassumpcao.orderflow.orders.application.query;

import com.caiodassumpcao.orderflow.orders.application.port.OrderStatistics;
import com.caiodassumpcao.orderflow.orders.application.port.OrderStatistics.DailyOrders;
import com.caiodassumpcao.orderflow.orders.application.port.OrderStatistics.StatusTotal;
import com.caiodassumpcao.orderflow.orders.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dashboard da operação sobre o modelo de leitura (eventualmente consistente). Receita confirmada
 * soma os pedidos {@code CONFIRMED} e {@code SHIPPED}: os que já têm estoque garantido.
 */
public class DashboardQueryService {

  public static final int DEFAULT_DAYS = 7;
  public static final int MAX_DAYS = 31;

  static final Set<OrderStatus> REVENUE_STATUSES =
      EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.SHIPPED);

  private final OrderStatistics statistics;
  private final Clock clock;

  public DashboardQueryService(OrderStatistics statistics, Clock clock) {
    this.statistics = Objects.requireNonNull(statistics);
    this.clock = Objects.requireNonNull(clock);
  }

  /**
   * Resumo dos pedidos.
   *
   * @param zone fuso usado para separar os dias
   * @param days dias da série diária, incluindo hoje (1 a {@link #MAX_DAYS})
   */
  public DashboardSummary summary(ZoneId zone, int days) {
    Objects.requireNonNull(zone, "zone");
    if (days < 1 || days > MAX_DAYS) {
      throw new IllegalArgumentException("Days must be between 1 and " + MAX_DAYS);
    }
    Instant now = clock.instant();
    LocalDate today = LocalDate.ofInstant(now, zone);
    LocalDate firstDay = today.minusDays(days - 1L);

    List<StatusTotal> totals = statistics.totalsByStatus();
    Map<OrderStatus, Long> byStatus = new EnumMap<>(OrderStatus.class);
    for (OrderStatus status : OrderStatus.values()) {
      byStatus.put(status, 0L);
    }
    Map<Currency, BigDecimal> revenue =
        new TreeMap<>(Comparator.comparing(Currency::getCurrencyCode));
    long totalOrders = 0;
    for (StatusTotal total : totals) {
      byStatus.merge(total.status(), total.orders(), Long::sum);
      totalOrders += total.orders();
      if (REVENUE_STATUSES.contains(total.status())) {
        revenue.merge(total.currency(), total.amount(), BigDecimal::add);
      }
    }

    Map<LocalDate, DailyOrders> byDay =
        statistics.dailyOrders(firstDay.atStartOfDay(zone).toInstant(), zone).stream()
            .collect(Collectors.toMap(DailyOrders::date, Function.identity()));
    List<DailyOrders> daily = new ArrayList<>(days);
    for (LocalDate day = firstDay; !day.isAfter(today); day = day.plusDays(1)) {
      daily.add(byDay.getOrDefault(day, new DailyOrders(day, 0, 0)));
    }

    return new DashboardSummary(
        now,
        zone,
        totalOrders,
        byStatus,
        revenue.entrySet().stream()
            .map(entry -> new DashboardSummary.Revenue(entry.getKey(), entry.getValue()))
            .toList(),
        daily);
  }
}

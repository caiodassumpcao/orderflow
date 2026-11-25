package com.caiodassumpcao.orderflow.orders.adapter.web;

import com.caiodassumpcao.orderflow.orders.application.port.OrderStatistics.DailyOrders;
import com.caiodassumpcao.orderflow.orders.application.query.DashboardQueryService;
import com.caiodassumpcao.orderflow.orders.application.query.DashboardSummary;
import com.caiodassumpcao.orderflow.shared.security.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Métricas agregadas de pedidos para a operação (papel ADMIN). */
@RestController
@RequestMapping(path = "/api/v1/dashboard", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Admin")
class DashboardController {

  /** Fusos por região do banco IANA (ex.: America/Sao_Paulo) ou UTC. */
  static final String TIME_ZONE_REGEX = "^(UTC|[A-Za-z_]{2,32}(/[A-Za-z0-9_+-]{1,32}){1,2})$";

  private final DashboardQueryService dashboard;

  DashboardController(DashboardQueryService dashboard) {
    this.dashboard = dashboard;
  }

  @GetMapping("/summary")
  @PreAuthorize(Roles.HAS_ADMIN)
  @Operation(
      operationId = "getDashboardSummary",
      summary = "Resumo dos pedidos: status, receita confirmada e pedidos por dia",
      description = "Eventualmente consistente, como as demais consultas.")
  DashboardSummaryResponse summary(
      @Parameter(description = "Dias da série diária, incluindo hoje (1 a 31)")
          @RequestParam(defaultValue = "" + DashboardQueryService.DEFAULT_DAYS)
          @Min(1)
          @Max(DashboardQueryService.MAX_DAYS)
          int days,
      @Parameter(description = "Fuso IANA que separa os dias, ex.: America/Sao_Paulo")
          @RequestParam(defaultValue = "UTC")
          @Pattern(regexp = TIME_ZONE_REGEX, message = "must be an IANA time zone")
          String timeZone) {
    return DashboardSummaryResponse.of(dashboard.summary(zone(timeZone), days));
  }

  private static ZoneId zone(String timeZone) {
    try {
      return ZoneId.of(timeZone);
    } catch (DateTimeException ex) {
      throw new InvalidTimeZoneException(timeZone, ex);
    }
  }

  /** Fuso com formato válido, mas que não existe no banco de fusos. */
  static class InvalidTimeZoneException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    InvalidTimeZoneException(String timeZone, Throwable cause) {
      super("Unknown time zone " + timeZone, cause);
    }
  }

  /**
   * Resumo dos pedidos.
   *
   * @param ordersByStatus pedidos por status, com todos os status
   * @param confirmedRevenue soma dos pedidos confirmados ou enviados, por moeda
   * @param daily pedidos criados por dia, do mais antigo para hoje
   */
  record DashboardSummaryResponse(
      Instant generatedAt,
      String timeZone,
      long totalOrders,
      Map<String, Long> ordersByStatus,
      List<Revenue> confirmedRevenue,
      List<Day> daily) {

    static DashboardSummaryResponse of(DashboardSummary summary) {
      Map<String, Long> byStatus = new LinkedHashMap<>();
      summary.ordersByStatus().forEach((status, count) -> byStatus.put(status.name(), count));
      return new DashboardSummaryResponse(
          summary.generatedAt(),
          summary.zone().getId(),
          summary.totalOrders(),
          byStatus,
          summary.confirmedRevenue().stream()
              .map(r -> new Revenue(r.currency().getCurrencyCode(), r.amount()))
              .toList(),
          summary.daily().stream().map(Day::of).toList());
    }
  }

  /** Receita numa moeda. */
  @Schema(name = "RevenueByCurrency")
  record Revenue(String currency, BigDecimal amount) {}

  /**
   * Pedidos criados num dia.
   *
   * @param cancelled quantos desses pedidos estão cancelados
   */
  @Schema(name = "DailyOrders")
  record Day(LocalDate date, long orders, long cancelled) {

    static Day of(DailyOrders daily) {
      return new Day(daily.date(), daily.orders(), daily.cancelled());
    }
  }
}

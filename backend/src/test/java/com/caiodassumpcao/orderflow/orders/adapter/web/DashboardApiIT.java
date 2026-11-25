package com.caiodassumpcao.orderflow.orders.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.caiodassumpcao.orderflow.TestTokens;
import com.caiodassumpcao.orderflow.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Dashboard sobre o modelo de leitura. O banco é compartilhado entre os testes: a receita usa uma
 * moeda que só este teste grava (CHF) e a série diária é conferida pela diferença num dia passado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class DashboardApiIT {

  private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

  @Autowired private MockMvc mvc;
  @Autowired private JdbcClient jdbc;

  private void project(String status, String total, OffsetDateTime createdAt) {
    jdbc.sql(
            """
            INSERT INTO query.order_summaries
                   (order_id, customer_id, status, currency, total_amount, item_count,
                    created_at, updated_at, last_event_version)
            VALUES (?, 'dash-customer', ?, 'CHF', ?, 1, ?, ?, 1)
            """)
        .params(UUID.randomUUID(), status, new BigDecimal(total), createdAt, createdAt)
        .update();
  }

  private String summary() throws Exception {
    return mvc.perform(
            get("/api/v1/dashboard/summary")
                .param("days", "31")
                .param("timeZone", SAO_PAULO.getId())
                .with(TestTokens.admin("dash-admin")))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private static long dayValue(String json, LocalDate day, String field) {
    List<Number> values = JsonPath.read(json, "$.daily[?(@.date == '" + day + "')]." + field);
    assertThat(values).hasSize(1);
    return values.getFirst().longValue();
  }

  @Test
  void summarizesStatusesConfirmedRevenueAndOrdersPerLocalDay() throws Exception {
    LocalDate day = LocalDate.now(SAO_PAULO).minusDays(20);
    // 23:30 em São Paulo já é o dia seguinte em UTC: o dia tem de ser o local.
    OffsetDateTime lateEvening = day.atTime(23, 30).atZone(SAO_PAULO).toOffsetDateTime();
    String before = summary();

    project("CONFIRMED", "10.00", lateEvening);
    project("SHIPPED", "5.50", lateEvening);
    project("CANCELLED", "99.00", lateEvening);
    String after = summary();

    assertThat(dayValue(after, day, "orders") - dayValue(before, day, "orders")).isEqualTo(3);
    assertThat(dayValue(after, day, "cancelled") - dayValue(before, day, "cancelled")).isEqualTo(1);
    List<Number> chf = JsonPath.read(after, "$.confirmedRevenue[?(@.currency == 'CHF')].amount");
    assertThat(chf).singleElement().satisfies(v -> assertThat(v.doubleValue()).isEqualTo(15.50));
    long totalBefore = ((Number) JsonPath.read(before, "$.totalOrders")).longValue();
    long totalAfter = ((Number) JsonPath.read(after, "$.totalOrders")).longValue();
    assertThat(totalAfter - totalBefore).isGreaterThanOrEqualTo(3);
  }

  @Test
  void returnsEveryStatusAndADayPerPeriodDay() throws Exception {
    mvc.perform(
            get("/api/v1/dashboard/summary")
                .param("days", "3")
                .with(TestTokens.admin("dash-admin")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.timeZone").value("UTC"))
        .andExpect(jsonPath("$.generatedAt").exists())
        .andExpect(jsonPath("$.daily", hasSize(3)))
        .andExpect(jsonPath("$.ordersByStatus.CREATED", greaterThanOrEqualTo(0)))
        .andExpect(jsonPath("$.ordersByStatus.STOCK_RESERVED", greaterThanOrEqualTo(0)))
        .andExpect(jsonPath("$.ordersByStatus.CONFIRMED", greaterThanOrEqualTo(0)))
        .andExpect(jsonPath("$.ordersByStatus.SHIPPED", greaterThanOrEqualTo(0)))
        .andExpect(jsonPath("$.ordersByStatus.CANCELLED", greaterThanOrEqualTo(0)));
  }

  @Test
  void validatesThePeriodAndTheTimeZone() throws Exception {
    mvc.perform(get("/api/v1/dashboard/summary").param("days", "0").with(TestTokens.admin("dash")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("days"));
    for (String zone : new String[] {"Mars/Olympus_Mons", "not a zone", "+03:00"}) {
      mvc.perform(
              get("/api/v1/dashboard/summary")
                  .param("timeZone", zone)
                  .with(TestTokens.admin("dash")))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].field").value("timeZone"));
    }
  }

  @Test
  void isReservedToAdmins() throws Exception {
    mvc.perform(get("/api/v1/dashboard/summary").with(TestTokens.customer("curious")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/dashboard/summary")).andExpect(status().isUnauthorized());
  }
}

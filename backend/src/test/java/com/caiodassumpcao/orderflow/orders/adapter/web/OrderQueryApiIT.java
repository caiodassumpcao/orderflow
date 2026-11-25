package com.caiodassumpcao.orderflow.orders.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.caiodassumpcao.orderflow.TestcontainersConfiguration;
import com.caiodassumpcao.orderflow.orders.application.port.OrderProjectionStore;
import com.caiodassumpcao.orderflow.orders.application.port.UnitOfWork;
import com.caiodassumpcao.orderflow.orders.application.projection.ProjectionEvent;
import com.caiodassumpcao.orderflow.orders.domain.CustomerId;
import com.caiodassumpcao.orderflow.orders.domain.OrderId;
import com.caiodassumpcao.orderflow.orders.domain.OrderStatus;
import com.caiodassumpcao.orderflow.shared.error.Problems;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Endpoints de consulta sobre um modelo de leitura preenchido direto pela projeção. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OrderQueryApiIT {

  private static final String ORDERS = "/api/v1/orders";
  private static final Instant T0 = Instant.parse("2026-11-24T12:00:00Z");

  @Autowired private MockMvc mvc;
  @Autowired private OrderProjectionStore store;
  @Autowired private UnitOfWork unitOfWork;
  @Autowired private JsonMapper json;

  private static JwtRequestPostProcessor as(String subject) {
    return jwt().jwt(token -> token.subject(subject));
  }

  private OrderId seed(String customer, int secondsAfterT0) {
    OrderId id = new OrderId(UUID.randomUUID());
    unitOfWork.inTransaction(() -> {
      store.applyCreated(
          new ProjectionEvent.OrderCreated(
              id,
              0,
              T0.plusSeconds(secondsAfterT0),
              new CustomerId(customer),
              Currency.getInstance("BRL"),
              new BigDecimal("59.70"),
              List.of(new ProjectionEvent.Item("SKU-001", 3, new BigDecimal("19.90")))));
      return null;
    });
    return id;
  }

  @Test
  void pagesThroughTheCustomersOrdersUsingTheReturnedCursor() throws Exception {
    String customer = "q-" + UUID.randomUUID();
    List<String> expected = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      expected.add(0, seed(customer, i).toString());
    }
    seed("someone-else-" + UUID.randomUUID(), 10);

    List<String> seen = new ArrayList<>();
    String cursor = null;
    int pages = 0;
    do {
      var request = get(ORDERS).with(as(customer)).param("limit", "2");
      if (cursor != null) {
        request = request.param("cursor", cursor);
      }
      JsonNode page =
          json.readTree(
              mvc.perform(request)
                  .andExpect(status().isOk())
                  .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                  .andReturn()
                  .getResponse()
                  .getContentAsString());
      page.get("items").forEach(item -> seen.add(item.get("id").asString()));
      cursor = page.get("nextCursor").isNull() ? null : page.get("nextCursor").asString();
      pages++;
    } while (cursor != null);

    assertThat(seen).containsExactlyElementsOf(expected);
    assertThat(pages).isEqualTo(3);
  }

  @Test
  void describesEachOrderInTheList() throws Exception {
    String customer = "q-" + UUID.randomUUID();
    OrderId id = seed(customer, 0);

    mvc.perform(get(ORDERS).with(as(customer)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].id").value(id.toString()))
        .andExpect(jsonPath("$.items[0].status").value("CREATED"))
        .andExpect(jsonPath("$.items[0].currency").value("BRL"))
        .andExpect(jsonPath("$.items[0].total").value(59.70))
        .andExpect(jsonPath("$.items[0].itemCount").value(1))
        .andExpect(jsonPath("$.items[0].createdAt").value("2026-11-24T12:00:00Z"))
        .andExpect(jsonPath("$.nextCursor").doesNotExist());
  }

  @Test
  void filtersByStatus() throws Exception {
    String customer = "q-" + UUID.randomUUID();
    seed(customer, 0);
    OrderId cancelled = seed(customer, 1);
    unitOfWork.inTransaction(() -> {
      store.applyStatusChange(
          new ProjectionEvent.StatusChanged(
              cancelled, 1, T0.plusSeconds(5), OrderStatus.CANCELLED, "no longer needed"));
      return null;
    });

    mvc.perform(get(ORDERS).with(as(customer)).param("status", "CANCELLED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].id").value(cancelled.toString()));
  }

  @Test
  void returnsTheDetailInTheSameShapeAsTheCommands() throws Exception {
    String customer = "q-" + UUID.randomUUID();
    OrderId id = seed(customer, 0);

    mvc.perform(get(ORDERS + "/{id}", id).with(as(customer)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id.toString()))
        .andExpect(jsonPath("$.status").value("CREATED"))
        .andExpect(jsonPath("$.total").value(59.70))
        .andExpect(jsonPath("$.items[0].sku").value("SKU-001"))
        .andExpect(jsonPath("$.items[0].quantity").value(3))
        .andExpect(jsonPath("$.items[0].total").value(59.70));
  }

  @Test
  void hidesOtherCustomersOrders() throws Exception {
    OrderId id = seed("owner-" + UUID.randomUUID(), 0);

    mvc.perform(get(ORDERS + "/{id}", id).with(as("intruder")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value(Problems.TYPE_BASE + "order-not-found"));
  }

  @Test
  void rejectsATamperedCursor() throws Exception {
    mvc.perform(get(ORDERS).with(as("alice")).param("cursor", "not-a-cursor"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(Problems.TYPE_BASE + "validation-error"))
        .andExpect(jsonPath("$.errors[0].field").value("cursor"));
  }

  @Test
  void rejectsInvalidPageSizesAndStatuses() throws Exception {
    mvc.perform(get(ORDERS).with(as("alice")).param("limit", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("limit"));
    mvc.perform(get(ORDERS).with(as("alice")).param("limit", "101"))
        .andExpect(status().isBadRequest());
    mvc.perform(get(ORDERS).with(as("alice")).param("status", "LOST"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @Test
  void requiresAuthentication() throws Exception {
    mvc.perform(get(ORDERS)).andExpect(status().isUnauthorized());
  }
}

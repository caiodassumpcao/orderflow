package com.caiodassumpcao.orderflow.orders.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.caiodassumpcao.orderflow.TestTokens;
import com.caiodassumpcao.orderflow.TestcontainersConfiguration;
import com.caiodassumpcao.orderflow.shared.error.Problems;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OrderCommandApiIT {

  private static final String ORDERS = "/api/v1/orders";
  private static final String VALID_BODY =
      """
      {"currency": "BRL",
       "items": [{"sku": "SKU-001", "quantity": 2, "unitPrice": 19.90},
                 {"sku": "SKU-002", "quantity": 1, "unitPrice": 100}]}
      """;

  @Autowired private MockMvc mvc;
  @Autowired private JdbcClient jdbc;
  @Autowired private JsonMapper json;

  private static JwtRequestPostProcessor customer(String subject) {
    return TestTokens.customer(subject);
  }

  private static String newKey() {
    return "key-" + UUID.randomUUID();
  }

  private ResultActions place(String subject, String key, String body) throws Exception {
    return mvc.perform(
        post(ORDERS)
            .with(customer(subject))
            .header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  /** Cria um pedido e devolve o id. */
  private String createOrder(String subject) throws Exception {
    String response =
        place(subject, newKey(), VALID_BODY)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(response).get("id").asString();
  }

  private static ResultActions expectProblem(ResultActions actions, int status, String type)
      throws Exception {
    return actions
        .andExpect(status().is(status))
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(status))
        .andExpect(jsonPath("$.type").value(Problems.TYPE_BASE + type))
        .andExpect(jsonPath("$.correlationId").exists());
  }

  @Nested
  class PlacingOrders {

    @Test
    void createsTheOrderAndPointsToIt() throws Exception {
      String response =
          place("alice", newKey(), VALID_BODY)
              .andExpect(status().isCreated())
              .andExpect(header().string("Idempotency-Replayed", "false"))
              .andExpect(content().contentType(MediaType.APPLICATION_JSON))
              .andExpect(jsonPath("$.status").value("CREATED"))
              .andExpect(jsonPath("$.currency").value("BRL"))
              .andExpect(jsonPath("$.total").value(139.80))
              .andExpect(jsonPath("$.items", hasSize(2)))
              .andExpect(jsonPath("$.items[0].sku").value("SKU-001"))
              .andExpect(jsonPath("$.items[0].total").value(39.80))
              .andExpect(jsonPath("$.cancellationReason").doesNotExist())
              .andReturn()
              .getResponse()
              .getHeader("Location");

      assertThat(response).startsWith(ORDERS + "/");
    }

    @Test
    void returnsTheSameOrderWhenTheRequestIsRetried() throws Exception {
      String key = newKey();
      JsonNode first =
          json.readTree(
              place("bob", key, VALID_BODY).andReturn().getResponse().getContentAsString());

      place("bob", key, VALID_BODY)
          .andExpect(status().isCreated())
          .andExpect(header().string("Idempotency-Replayed", "true"))
          .andExpect(header().string("Location", endsWith(first.get("id").asString())))
          .andExpect(jsonPath("$.id").value(first.get("id").asString()));
    }

    @Test
    void rejectsAKeyReusedWithAnotherBody() throws Exception {
      String key = newKey();
      place("carol", key, VALID_BODY).andExpect(status().isCreated());

      expectProblem(
              place(
                  "carol",
                  key,
                  """
                  {"currency": "BRL", "items": [{"sku": "SKU-001", "quantity": 9, "unitPrice": 1}]}
                  """),
              422,
              "idempotency-key-reused")
          .andExpect(jsonPath("$.title").value("Idempotency key reused"));
    }

    @Test
    void requiresTheIdempotencyKeyHeader() throws Exception {
      mvc.perform(
              post(ORDERS)
                  .with(customer("dave"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(VALID_BODY))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void rejectsAMalformedIdempotencyKey() throws Exception {
      expectProblem(place("dave", "short", VALID_BODY), 400, "validation-error")
          .andExpect(jsonPath("$.errors[0].field").value("idempotencyKey"));
    }

    @Test
    void listsEveryInvalidField() throws Exception {
      expectProblem(
              place(
                  "erin",
                  newKey(),
                  """
                  {"items": [{"sku": "sku-1", "quantity": 0, "unitPrice": 1.999}]}
                  """),
              400,
              "validation-error")
          .andExpect(
              jsonPath("$.errors[*].field")
                  .value(
                      containsInAnyOrder(
                          "currency", "items[0].sku", "items[0].quantity", "items[0].unitPrice")));
    }

    @Test
    void rejectsAnOrderWithoutItems() throws Exception {
      expectProblem(
              place("erin", newKey(), "{\"currency\": \"BRL\", \"items\": []}"),
              400,
              "validation-error")
          .andExpect(jsonPath("$.errors[0].field").value("items"));
    }

    @Test
    void rejectsMalformedJson() throws Exception {
      place("erin", newKey(), "{\"currency\": ")
          .andExpect(status().isBadRequest())
          .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void enforcesDomainRulesThatTheRequestSchemaCannotExpress() throws Exception {
      expectProblem(
              place(
                  "frank",
                  newKey(),
                  """
                  {"currency": "BRL",
                   "items": [{"sku": "SKU-001", "quantity": 1, "unitPrice": 1},
                             {"sku": "SKU-001", "quantity": 2, "unitPrice": 1}]}
                  """),
              422,
              "invalid-order")
          .andExpect(jsonPath("$.detail").value("Duplicate SKU in order: SKU-001"));
    }

    @Test
    void rejectsUnknownCurrencies() throws Exception {
      expectProblem(
          place(
              "frank",
              newKey(),
              "{\"currency\": \"XYZ\", \"items\": [{\"sku\": \"SKU-001\", \"quantity\": 1,"
                  + " \"unitPrice\": 1}]}"),
          422,
          "invalid-order");
    }

    @Test
    void requiresAuthentication() throws Exception {
      mvc.perform(
              post(ORDERS)
                  .header("Idempotency-Key", newKey())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(VALID_BODY))
          .andExpect(status().isUnauthorized());
    }

    @Test
    void recordsTheRequestCorrelationIdOnTheOutboxEvent() throws Exception {
      String response =
          mvc.perform(
                  post(ORDERS)
                      .with(customer("grace"))
                      .header("Idempotency-Key", newKey())
                      .header("X-Correlation-Id", "corr-outbox-0001")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(VALID_BODY))
              .andExpect(status().isCreated())
              .andReturn()
              .getResponse()
              .getContentAsString();
      UUID orderId = UUID.fromString(json.readTree(response).get("id").asString());

      String correlationId =
          jdbc.sql("SELECT correlation_id FROM command.outbox WHERE aggregate_id = ?")
              .param(orderId)
              .query(String.class)
              .single();

      assertThat(correlationId).isEqualTo("corr-outbox-0001");
    }
  }

  @Nested
  class CancellingOrders {

    @Test
    void cancelsTheCustomersOwnOrder() throws Exception {
      String id = createOrder("heidi");

      mvc.perform(
              post(ORDERS + "/{id}/cancel", id)
                  .with(customer("heidi"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"reason\": \"Changed my mind\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("CANCELLED"))
          .andExpect(jsonPath("$.cancellationReason").value("Changed my mind"));
    }

    @Test
    void acceptsACancellationWithoutBody() throws Exception {
      String id = createOrder("ivan");

      mvc.perform(post(ORDERS + "/{id}/cancel", id).with(customer("ivan")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void hidesOrdersOfOtherCustomers() throws Exception {
      String id = createOrder("judy");

      expectProblem(
          mvc.perform(post(ORDERS + "/{id}/cancel", id).with(customer("mallory"))),
          404,
          "order-not-found");
    }

    @Test
    void refusesToCancelTwice() throws Exception {
      String id = createOrder("ken");
      mvc.perform(post(ORDERS + "/{id}/cancel", id).with(customer("ken")))
          .andExpect(status().isOk());

      expectProblem(
              mvc.perform(post(ORDERS + "/{id}/cancel", id).with(customer("ken"))),
              409,
              "order-state-conflict")
          .andExpect(jsonPath("$.currentStatus").value("CANCELLED"));
    }

    @Test
    void rejectsAReasonLongerThan200Characters() throws Exception {
      String id = createOrder("leo");

      expectProblem(
              mvc.perform(
                  post(ORDERS + "/{id}/cancel", id)
                      .with(customer("leo"))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"reason\": \"" + "x".repeat(201) + "\"}")),
              400,
              "validation-error")
          .andExpect(jsonPath("$.errors[0].field").value("reason"));
    }

    @Test
    void rejectsAnInvalidOrderId() throws Exception {
      mvc.perform(post(ORDERS + "/not-a-uuid/cancel").with(customer("leo")))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }
  }

  @Nested
  class ShippingOrders {

    @Test
    void refusesToShipAnOrderThatWasNotConfirmed() throws Exception {
      String id = createOrder("mike");

      expectProblem(
              mvc.perform(post(ORDERS + "/{id}/ship", id).with(TestTokens.admin("ops"))),
              409,
              "order-state-conflict")
          .andExpect(jsonPath("$.currentStatus").value("CREATED"))
          .andExpect(jsonPath("$.instance").value(ORDERS + "/" + id + "/ship"));
    }

    @Test
    void reportsUnknownOrders() throws Exception {
      expectProblem(
          mvc.perform(post(ORDERS + "/{id}/ship", UUID.randomUUID()).with(TestTokens.admin("ops"))),
          404,
          "order-not-found");
    }
  }
}

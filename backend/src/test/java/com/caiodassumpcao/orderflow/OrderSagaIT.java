package com.caiodassumpcao.orderflow;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;
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
import tools.jackson.databind.json.JsonMapper;

/**
 * Saga de estoque ponta a ponta com Kafka real: pedido → inventário → pedido, observada só pela API
 * (comandos, consulta de pedidos e de estoque).
 */
@SpringBootTest(properties = "orderflow.messaging.relay.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OrderSagaIT {

  private static final Duration TIMEOUT = Duration.ofSeconds(40);

  @Autowired private MockMvc mvc;
  @Autowired private JdbcClient jdbc;
  @Autowired private JsonMapper json;

  private static JwtRequestPostProcessor as(String subject) {
    return TestTokens.customer(subject);
  }

  /** Produto exclusivo do teste, para que os testes não disputem o mesmo estoque. */
  private String product(String price, int available) {
    String sku = "SAGA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    jdbc.sql(
            "INSERT INTO command.stock_items (sku, currency, unit_price, available)"
                + " VALUES (?, 'BRL', CAST(? AS numeric), ?)")
        .param(sku)
        .param(price)
        .param(available)
        .update();
    return sku;
  }

  private String placeOrder(String customer, String sku, int quantity, String unitPrice)
      throws Exception {
    String response =
        mvc.perform(
                post("/api/v1/orders")
                    .with(as(customer))
                    .header("Idempotency-Key", "key-" + UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"currency":"BRL",
                         "items":[{"sku":"%s","quantity":%d,"unitPrice":%s}]}
                        """
                            .formatted(sku, quantity, unitPrice)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("CREATED"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(response).get("id").asString();
  }

  private void awaitOrder(String customer, String id, String expectedStatus) {
    await()
        .atMost(TIMEOUT)
        .untilAsserted(() ->
            mvc.perform(get("/api/v1/orders/{id}", id).with(as(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(expectedStatus)));
  }

  @Test
  void reservesStockAndConfirmsTheOrder() throws Exception {
    String sku = product("19.90", 10);
    String customer = "saga-" + UUID.randomUUID();

    String id = placeOrder(customer, sku, 3, "19.90");

    awaitOrder(customer, id, "CONFIRMED");
    mvc.perform(get("/api/v1/inventory/{sku}", sku).with(TestTokens.admin("ops")))
        .andExpect(jsonPath("$.available").value(7))
        .andExpect(jsonPath("$.reserved").value(3));
  }

  @Test
  void cancelsTheOrderWhenStockIsShort() throws Exception {
    String sku = product("19.90", 2);
    String customer = "saga-" + UUID.randomUUID();

    String id = placeOrder(customer, sku, 3, "19.90");

    awaitOrder(customer, id, "CANCELLED");
    mvc.perform(get("/api/v1/orders/{id}", id).with(as(customer)))
        .andExpect(
            jsonPath("$.cancellationReason").value("Stock rejected: OUT_OF_STOCK (" + sku + ")"));
    mvc.perform(get("/api/v1/inventory/{sku}", sku).with(TestTokens.admin("ops")))
        .andExpect(jsonPath("$.available").value(2))
        .andExpect(jsonPath("$.reserved").value(0));
  }

  @Test
  void cancelsTheOrderWhenThePriceDiffersFromTheCatalog() throws Exception {
    String sku = product("19.90", 10);
    String customer = "saga-" + UUID.randomUUID();

    String id = placeOrder(customer, sku, 1, "0.01");

    awaitOrder(customer, id, "CANCELLED");
    mvc.perform(get("/api/v1/orders/{id}", id).with(as(customer)))
        .andExpect(
            jsonPath("$.cancellationReason").value("Stock rejected: PRICE_MISMATCH (" + sku + ")"));
  }

  @Test
  void shippingAConfirmedOrderTakesTheReservedUnitsOutOfStock() throws Exception {
    String sku = product("5.00", 10);
    String customer = "saga-" + UUID.randomUUID();
    String id = placeOrder(customer, sku, 4, "5.00");
    awaitOrder(customer, id, "CONFIRMED");

    mvc.perform(post("/api/v1/orders/{id}/ship", id).with(TestTokens.admin("warehouse")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SHIPPED"));

    awaitOrder(customer, id, "SHIPPED");
    await()
        .atMost(TIMEOUT)
        .untilAsserted(() ->
            mvc.perform(get("/api/v1/inventory/{sku}", sku).with(TestTokens.admin("ops")))
                .andExpect(jsonPath("$.available").value(6))
                .andExpect(jsonPath("$.reserved").value(0)));
  }
}

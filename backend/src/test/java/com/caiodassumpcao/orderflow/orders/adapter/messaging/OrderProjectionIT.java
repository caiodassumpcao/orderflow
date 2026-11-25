package com.caiodassumpcao.orderflow.orders.adapter.messaging;

import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.caiodassumpcao.orderflow.TestcontainersConfiguration;
import com.caiodassumpcao.orderflow.shared.messaging.EventEnvelope;
import com.caiodassumpcao.orderflow.shared.messaging.EventEnvelopeCodec;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** CQRS ponta a ponta com Kafka real: comando HTTP → outbox → relay → projector → consulta HTTP. */
@SpringBootTest(
    properties = {
      "orderflow.messaging.relay.enabled=true",
      // Isola o que o teste observa: sem a saga, nenhum evento extra é gerado para os pedidos.
      "orderflow.messaging.consumers.inventory.enabled=false",
      "orderflow.messaging.consumers.order-saga.enabled=false"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OrderProjectionIT {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  @Autowired private MockMvc mvc;
  @Autowired private KafkaTemplate<String, String> kafka;
  @Autowired private EventEnvelopeCodec codec;
  @Autowired private JsonMapper json;

  private static JwtRequestPostProcessor as(String subject) {
    return jwt().jwt(token -> token.subject(subject));
  }

  @Test
  void ordersPlacedAndCancelledThroughTheApiShowUpInTheQueries() throws Exception {
    String customer = "proj-" + UUID.randomUUID();
    String response =
        mvc.perform(
                post("/api/v1/orders")
                    .with(as(customer))
                    .header("Idempotency-Key", "key-" + UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"currency":"BRL",
                         "items":[{"sku":"SKU-001","quantity":2,"unitPrice":19.90}]}
                        """))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = json.readTree(response).get("id").asString();

    await()
        .atMost(TIMEOUT)
        .untilAsserted(() ->
            mvc.perform(get("/api/v1/orders").with(as(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(id))
                .andExpect(jsonPath("$.items[0].status").value("CREATED"))
                .andExpect(jsonPath("$.items[0].total").value(39.80)));

    mvc.perform(
            post("/api/v1/orders/{id}/cancel", id)
                .with(as(customer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"changed my mind\"}"))
        .andExpect(status().isOk());

    await()
        .atMost(TIMEOUT)
        .untilAsserted(() ->
            mvc.perform(get("/api/v1/orders/{id}", id).with(as(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("changed my mind")));
  }

  @Test
  void endsInTheRightStateWhenEventsArriveOutOfOrder() throws Exception {
    String customer = "ooo-" + UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    String key = orderId.toString();

    kafka.send(
        OrderTopics.ORDER_EVENTS,
        key,
        envelope(
            orderId,
            "OrderCancelled",
            1,
            "{\"orderId\":\"%s\",\"previousStatus\":\"CREATED\",\"reason\":\"late\"}"
                .formatted(orderId)));
    kafka.send(
        OrderTopics.ORDER_EVENTS,
        key,
        envelope(
            orderId,
            "OrderCreated",
            0,
            """
            {"orderId":"%s","customerId":"%s","currency":"BRL","total":"10.00",
             "items":[{"sku":"SKU-009","quantity":1,"unitPrice":"10.00"}]}
            """
                .formatted(orderId, customer)));

    await()
        .atMost(TIMEOUT)
        .untilAsserted(() ->
            mvc.perform(get("/api/v1/orders/{id}", orderId).with(as(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("late"))
                .andExpect(jsonPath("$.items[0].sku").value("SKU-009")));
  }

  private String envelope(UUID orderId, String type, long aggregateVersion, String payload) {
    return codec.encode(
        new EventEnvelope(
            UUID.randomUUID(),
            type,
            1,
            orderId,
            aggregateVersion,
            Instant.now(),
            null,
            json.readTree(payload)));
  }
}

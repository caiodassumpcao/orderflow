package com.caiodassumpcao.orderflow.shared.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.caiodassumpcao.orderflow.TestcontainersConfiguration;
import com.caiodassumpcao.orderflow.orders.adapter.messaging.OrderTopics;
import com.caiodassumpcao.orderflow.orders.application.OrderCommandService;
import com.caiodassumpcao.orderflow.orders.application.PlaceOrderCommand;
import com.caiodassumpcao.orderflow.orders.domain.CustomerId;
import com.caiodassumpcao.orderflow.orders.domain.Order;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

/**
 * Mensageria ponta a ponta com Kafka e PostgreSQL reais: relay da outbox e um consumer de teste
 * montado com a mesma receita dos consumers reais (retry, idempotência e DLQ).
 */
@SpringBootTest(
    properties = {
      "orderflow.messaging.relay.enabled=true",
      // Isola o que o teste observa: sem a saga, nenhum evento extra é gerado para os pedidos.
      "orderflow.messaging.consumers.inventory.enabled=false",
      "orderflow.messaging.consumers.order-saga.enabled=false"
    })
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, MessagingIT.ProbeConsumer.class})
class MessagingIT {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  @Autowired private OrderCommandService orders;
  @Autowired private KafkaTemplate<String, String> kafka;
  @Autowired private ConsumerFactory<String, String> consumerFactory;
  @Autowired private EventEnvelopeCodec codec;
  @Autowired private JsonMapper json;
  @Autowired private JdbcClient jdbc;
  @Autowired private MeterRegistry meters;
  @Autowired private ProbeConsumer probe;

  @Test
  void relayPublishesEachOrdersEventsInWriteOrderKeyedByTheOrderId() {
    CustomerId customer = new CustomerId("relay-" + UUID.randomUUID());
    Order order =
        orders
            .placeOrder(
                new PlaceOrderCommand(
                    customer,
                    "key-" + UUID.randomUUID(),
                    "BRL",
                    List.of(new PlaceOrderCommand.Item("SKU-001", 2, new BigDecimal("19.90")))))
            .order();
    orders.cancelOrder(order.id(), customer, "changed my mind");
    String key = order.id().toString();

    List<ConsumerRecord<String, String>> published =
        readFromStart(OrderTopics.ORDER_EVENTS, key, 2);

    List<EventEnvelope> envelopes = published.stream().map(r -> codec.decode(r.value())).toList();
    assertThat(envelopes)
        .extracting(EventEnvelope::type)
        .containsExactly("OrderCreated", "OrderCancelled");
    assertThat(envelopes).extracting(EventEnvelope::aggregateVersion).containsExactly(0L, 1L);
    assertThat(envelopes)
        .allSatisfy(e -> assertThat(e.aggregateId()).isEqualTo(order.id().value()));
    assertThat(envelopes.getFirst().payload().get("total").asString()).isEqualTo("39.80");
    assertThat(published)
        .extracting(ConsumerRecord::partition)
        .containsOnly(published.getFirst().partition());
    assertThat(
            jdbc.sql(
                    "SELECT id FROM command.outbox"
                        + " WHERE aggregate_id = ? AND published_at IS NOT NULL ORDER BY position")
                .param(order.id().value())
                .query(UUID.class)
                .list())
        .containsExactlyElementsOf(envelopes.stream().map(EventEnvelope::eventId).toList());
  }

  @Test
  void processesARedeliveredEventOnlyOnce() {
    String key = UUID.randomUUID().toString();
    String message = envelopeFor(key);
    double duplicatesBefore = counter("orderflow.messaging.duplicates");

    kafka.send(OrderTopics.ORDER_EVENTS, key, message);
    kafka.send(OrderTopics.ORDER_EVENTS, key, message);

    await()
        .atMost(TIMEOUT)
        .untilAsserted(() ->
            assertThat(counter("orderflow.messaging.duplicates")).isEqualTo(duplicatesBefore + 1));
    assertThat(probe.handled(key)).isEqualTo(1);
    assertThat(processedRows(message)).isEqualTo(1);
  }

  @Test
  void retriesATransientFailureThroughTheRetryTopicsAndThenSucceeds() {
    String key = UUID.randomUUID().toString();
    probe.failTimes(key, 2);
    String message = envelopeFor(key);

    kafka.send(OrderTopics.ORDER_EVENTS, key, message);

    await().atMost(TIMEOUT).untilAsserted(() -> assertThat(probe.handled(key)).isEqualTo(1));
    assertThat(probe.deliveries(key))
        .containsExactly(
            "order.events", "order.events.probe.retry-0", "order.events.probe.retry-1");
    assertThat(processedRows(message)).isEqualTo(1);
    assertThat(probe.deadLetters()).doesNotContain(key);
  }

  @Test
  void movesAMessageThatKeepsFailingToTheConsumersDeadLetterTopic() {
    String key = UUID.randomUUID().toString();
    probe.failTimes(key, Integer.MAX_VALUE);
    String message = envelopeFor(key);
    double deadBefore = counter("orderflow.messaging.dead.letters");

    kafka.send(OrderTopics.ORDER_EVENTS, key, message);

    await().atMost(TIMEOUT).untilAsserted(() -> assertThat(probe.deadLetters()).contains(key));
    assertThat(probe.deliveries(key))
        .hasSize(3); // orderflow.messaging.retry.attempts no perfil test
    assertThat(probe.deadLetterTopics()).contains("order.events.probe.dlq");
    assertThat(probe.deadLetterReason(key)).contains("simulated transient failure");
    assertThat(counter("orderflow.messaging.dead.letters")).isEqualTo(deadBefore + 1);
    assertThat(probe.handled(key)).isZero();
    assertThat(processedRows(message)).isZero();
  }

  @Test
  void sendsAPoisonMessageStraightToTheDeadLetterTopicWithoutRetrying() {
    String key = UUID.randomUUID().toString();

    kafka.send(OrderTopics.ORDER_EVENTS, key, "{ this is not an envelope");

    await().atMost(TIMEOUT).untilAsserted(() -> assertThat(probe.deadLetters()).contains(key));
    assertThat(probe.deliveries(key)).containsExactly("order.events");
    assertThat(probe.handled(key)).isZero();
  }

  private String envelopeFor(String aggregateId) {
    return codec.encode(
        new EventEnvelope(
            UUID.randomUUID(),
            "ProbeEvent",
            1,
            UUID.fromString(aggregateId),
            0,
            Instant.now(),
            "corr-" + aggregateId.substring(0, 8),
            json.readTree("{}")));
  }

  private long processedRows(String message) {
    return jdbc.sql(
            "SELECT count(*) FROM command.processed_events WHERE event_id = ? AND consumer = ?")
        .param(codec.decode(message).eventId())
        .param(ProbeConsumer.NAME)
        .query(Long.class)
        .single();
  }

  private double counter(String name) {
    return meters.counter(name, "consumer", ProbeConsumer.NAME).count();
  }

  private List<ConsumerRecord<String, String>> readFromStart(
      String topic, String key, int expected) {
    List<ConsumerRecord<String, String>> found = new ArrayList<>();
    try (Consumer<String, String> consumer =
        consumerFactory.createConsumer("verifier-" + UUID.randomUUID(), "verifier")) {
      consumer.subscribe(List.of(topic));
      await()
          .atMost(TIMEOUT)
          .until(() -> {
            consumer
                .poll(Duration.ofMillis(500))
                .forEach(record -> {
                  if (key.equals(record.key())) {
                    found.add(record);
                  }
                });
            return found.size() >= expected;
          });
    }
    return found;
  }

  /** Consumer de teste: mesma receita dos consumers reais, com falhas programáveis por chave. */
  static class ProbeConsumer {

    static final String NAME = "probe";

    private final IdempotentEventProcessor processor;
    private final DeadLetters deadLetters;
    private final Map<String, List<String>> deliveries = new ConcurrentHashMap<>();
    private final Map<String, Integer> failuresLeft = new ConcurrentHashMap<>();
    private final Map<String, Integer> handled = new ConcurrentHashMap<>();
    private final Set<String> dead = ConcurrentHashMap.newKeySet();
    private final Set<String> deadTopics = ConcurrentHashMap.newKeySet();
    private final Map<String, String> deadReasons = new ConcurrentHashMap<>();

    ProbeConsumer(IdempotentEventProcessor processor, DeadLetters deadLetters) {
      this.processor = processor;
      this.deadLetters = deadLetters;
    }

    @OrderflowRetryableTopic(retryTopicSuffix = ".probe.retry", dltTopicSuffix = ".probe.dlq")
    @KafkaListener(topics = OrderTopics.ORDER_EVENTS, groupId = NAME)
    void onMessage(ConsumerRecord<String, String> record) {
      deliveries
          .computeIfAbsent(record.key(), k -> new CopyOnWriteArrayList<>())
          .add(record.topic());
      processor.process(
          NAME,
          record.value(),
          envelope -> {
            Integer left = failuresLeft.computeIfPresent(record.key(), (k, n) -> n - 1);
            if (left != null && left >= 0) {
              throw new IllegalStateException("simulated transient failure");
            }
            handled.merge(record.key(), 1, Integer::sum);
          });
    }

    @DltHandler
    void onDeadLetter(
        ConsumerRecord<String, String> record,
        @Header(name = KafkaHeaders.EXCEPTION_MESSAGE, required = false) byte[] reason) {
      dead.add(record.key());
      deadTopics.add(record.topic());
      if (reason != null) {
        deadReasons.put(record.key(), new String(reason, StandardCharsets.UTF_8));
      }
      deadLetters.record(NAME, record.topic(), record.key(), reason);
    }

    void failTimes(String key, int times) {
      failuresLeft.put(key, times);
    }

    int handled(String key) {
      return handled.getOrDefault(key, 0);
    }

    List<String> deliveries(String key) {
      return List.copyOf(deliveries.getOrDefault(key, List.of()));
    }

    Set<String> deadLetters() {
      return Set.copyOf(dead);
    }

    Set<String> deadLetterTopics() {
      return Set.copyOf(deadTopics);
    }

    String deadLetterReason(String key) {
      return deadReasons.get(key);
    }
  }
}

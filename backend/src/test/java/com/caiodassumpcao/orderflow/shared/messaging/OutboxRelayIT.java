package com.caiodassumpcao.orderflow.shared.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.caiodassumpcao.orderflow.TestcontainersConfiguration;
import com.caiodassumpcao.orderflow.orders.application.OrderCommandService;
import com.caiodassumpcao.orderflow.orders.application.PlaceOrderCommand;
import com.caiodassumpcao.orderflow.orders.domain.CustomerId;
import com.caiodassumpcao.orderflow.orders.domain.OrderId;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * Falhas do lado do broker com PostgreSQL real e sem Kafka: o endereço configurado não tem broker,
 * e um {@link KafkaOperations} simulado reproduz confirmações parciais.
 */
@SpringBootTest(
    properties = {
      "spring.kafka.bootstrap-servers=localhost:1",
      "spring.kafka.admin.auto-create=false",
      "spring.kafka.producer.properties.max.block.ms=1000",
      "spring.kafka.producer.properties.request.timeout.ms=1000",
      "spring.kafka.producer.properties.delivery.timeout.ms=2000"
    })
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.Postgres.class)
class OutboxRelayIT {

  @Autowired private OutboxRelay relay;
  @Autowired private OrderCommandService orders;
  @Autowired private JdbcClient jdbc;
  @Autowired private JsonMapper json;
  @Autowired private EventEnvelopeCodec codec;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private MessagingProperties properties;
  @Autowired private MeterRegistry meters;

  @BeforeEach
  void startWithAnEmptyOutbox() {
    jdbc.sql("DELETE FROM command.outbox").update();
  }

  private OrderId placeOrder() {
    return orders
        .placeOrder(
            new PlaceOrderCommand(
                new CustomerId("relay-it"),
                "key-" + UUID.randomUUID(),
                "BRL",
                List.of(new PlaceOrderCommand.Item("SKU-001", 1, BigDecimal.TEN))))
        .order()
        .id();
  }

  private boolean isPublished(OrderId id) {
    return jdbc.sql("SELECT published_at IS NOT NULL FROM command.outbox WHERE aggregate_id = ?")
        .param(id.value())
        .query(Boolean.class)
        .single();
  }

  @Test
  void keepsEventsPendingAndTheApiWorkingWhileTheBrokerIsDown() {
    OrderId id = placeOrder();
    double failuresBefore = meters.counter("orderflow.outbox.publish.failures").count();

    int published = relay.publishBatch();

    assertThat(published).isZero();
    assertThat(isPublished(id)).isFalse();
    assertThat(meters.counter("orderflow.outbox.publish.failures").count())
        .isEqualTo(failuresBefore + 1);
    assertThat(meters.get("orderflow.outbox.pending").gauge().value()).isEqualTo(1.0);
  }

  @Test
  @SuppressWarnings("unchecked")
  void marksOnlyTheConfirmedPrefixAndStopsAtTheFirstFailure() {
    OrderId first = placeOrder();
    OrderId second = placeOrder();
    OrderId third = placeOrder();
    KafkaOperations<String, String> kafka = mock(KafkaOperations.class);
    when(kafka.send(any(ProducerRecord.class)))
        .thenReturn(
            CompletableFuture.completedFuture(new SendResult<>(null, null)),
            CompletableFuture.failedFuture(new TimeoutException("broker did not answer")),
            CompletableFuture.completedFuture(new SendResult<>(null, null)));
    SimpleMeterRegistry localMeters = new SimpleMeterRegistry();
    OutboxRelay partialRelay =
        new OutboxRelay(jdbc, kafka, json, codec, transactionManager, properties, localMeters);

    int published = partialRelay.publishBatch();

    assertThat(published).isEqualTo(1);
    assertThat(isPublished(first)).isTrue();
    assertThat(isPublished(second)).isFalse();
    assertThat(isPublished(third)).isFalse();
    assertThat(localMeters.counter("orderflow.outbox.publish.failures").count()).isEqualTo(1.0);

    ArgumentCaptor<ProducerRecord<String, String>> sent =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(kafka, times(3)).send(sent.capture());
    assertThat(sent.getAllValues())
        .extracting(ProducerRecord::key)
        .containsExactly(first.toString(), second.toString(), third.toString());
    assertThat(sent.getAllValues()).extracting(ProducerRecord::topic).containsOnly("order.events");
  }

  @Test
  void resumesFromTheFirstPendingEventOnTheNextRun() {
    OrderId first = placeOrder();
    OrderId second = placeOrder();
    KafkaOperations<String, String> kafka = mock(KafkaOperations.class);
    when(kafka.send(any(ProducerRecord.class)))
        .thenReturn(CompletableFuture.failedFuture(new TimeoutException("down")))
        .thenReturn(CompletableFuture.completedFuture(new SendResult<>(null, null)));
    OutboxRelay flakyRelay =
        new OutboxRelay(
            jdbc, kafka, json, codec, transactionManager, properties, new SimpleMeterRegistry());

    assertThat(flakyRelay.publishBatch()).isZero();
    assertThat(flakyRelay.publishBatch()).isEqualTo(2);

    assertThat(isPublished(first)).isTrue();
    assertThat(isPublished(second)).isTrue();
  }
}

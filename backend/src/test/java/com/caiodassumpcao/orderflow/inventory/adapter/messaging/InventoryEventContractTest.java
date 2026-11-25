package com.caiodassumpcao.orderflow.inventory.adapter.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.caiodassumpcao.orderflow.inventory.domain.InventoryEvent;
import com.caiodassumpcao.orderflow.inventory.domain.RejectionReason;
import com.caiodassumpcao.orderflow.inventory.domain.ReservationLine;
import com.caiodassumpcao.orderflow.inventory.domain.Sku;
import com.caiodassumpcao.orderflow.shared.messaging.EventContracts;
import com.caiodassumpcao.orderflow.shared.messaging.OutboxMessage;
import com.caiodassumpcao.orderflow.shared.messaging.OutboxWriter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

/** Produtor: tudo que o inventário grava na outbox respeita docs/events/schemas. */
class InventoryEventContractTest {

  private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");

  private final OutboxWriter writer = mock(OutboxWriter.class);
  private final OutboxInventoryEventPublisher publisher =
      new OutboxInventoryEventPublisher(writer, JsonMapper.builder().build());

  @Test
  void everyPublishedMessageMatchesTheEventContracts() {
    List<InventoryEvent> events = new ArrayList<>();
    events.add(
        new InventoryEvent.StockReserved(
            UUID.randomUUID(),
            0,
            List.of(new ReservationLine(new Sku("SKU-001"), 2, new BigDecimal("19.90"))),
            NOW));
    for (RejectionReason reason : RejectionReason.values()) {
      events.add(
          new InventoryEvent.StockRejected(
              UUID.randomUUID(), 0, reason, List.of(new Sku("SKU-005")), NOW));
    }

    publisher.append(events);

    ArgumentCaptor<OutboxMessage> written = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(writer, times(events.size())).append(written.capture());
    for (OutboxMessage message : written.getAllValues()) {
      assertThat(EventContracts.messageViolations(EventContracts.published(message)))
          .as("%s v%d", message.eventType(), message.eventVersion())
          .isEmpty();
    }
  }
}

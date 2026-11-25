package com.caiodassumpcao.orderflow.orders.application.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.caiodassumpcao.orderflow.orders.application.port.OrderProjectionStore;
import com.caiodassumpcao.orderflow.orders.domain.CustomerId;
import com.caiodassumpcao.orderflow.orders.domain.OrderId;
import com.caiodassumpcao.orderflow.orders.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderProjectionServiceTest {

  private final List<ProjectionEvent> applied = new ArrayList<>();
  private final OrderProjectionService service =
      new OrderProjectionService(
          new OrderProjectionStore() {
            @Override
            public void applyCreated(ProjectionEvent.OrderCreated event) {
              applied.add(event);
            }

            @Override
            public void applyStatusChange(ProjectionEvent.StatusChanged event) {
              applied.add(event);
            }
          });

  @Test
  void routesEachEventToTheMatchingStoreOperation() {
    OrderId id = new OrderId(UUID.randomUUID());
    ProjectionEvent created =
        new ProjectionEvent.OrderCreated(
            id,
            0,
            Instant.EPOCH,
            new CustomerId("alice"),
            Currency.getInstance("BRL"),
            BigDecimal.TEN,
            List.of(new ProjectionEvent.Item("SKU-001", 1, BigDecimal.TEN)));
    ProjectionEvent cancelled =
        new ProjectionEvent.StatusChanged(id, 1, Instant.EPOCH, OrderStatus.CANCELLED, "x");

    service.apply(cancelled);
    service.apply(created);

    assertThat(applied).containsExactly(cancelled, created);
  }
}

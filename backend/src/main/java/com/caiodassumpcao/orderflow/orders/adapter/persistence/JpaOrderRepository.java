package com.caiodassumpcao.orderflow.orders.adapter.persistence;

import com.caiodassumpcao.orderflow.orders.application.port.ConcurrentOrderUpdateException;
import com.caiodassumpcao.orderflow.orders.application.port.OrderRepository;
import com.caiodassumpcao.orderflow.orders.domain.CustomerId;
import com.caiodassumpcao.orderflow.orders.domain.Money;
import com.caiodassumpcao.orderflow.orders.domain.Order;
import com.caiodassumpcao.orderflow.orders.domain.OrderId;
import com.caiodassumpcao.orderflow.orders.domain.OrderLine;
import com.caiodassumpcao.orderflow.orders.domain.Sku;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter JPA do {@link OrderRepository}. Grava com flush imediato para que conflitos de versão e
 * violações de restrição apareçam dentro da transação do caso de uso.
 */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
class JpaOrderRepository implements OrderRepository {

  private final OrderJpaRepository jpa;

  JpaOrderRepository(OrderJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Optional<Order> findById(OrderId id) {
    return jpa.findById(id.value()).map(JpaOrderRepository::toDomain);
  }

  @Override
  public void save(Order order) {
    try {
      if (order.version().isEmpty()) {
        jpa.saveAndFlush(toNewEntity(order));
        return;
      }
      OrderJpaEntity entity =
          jpa.findById(order.id().value())
              .filter(found -> found.version().equals(order.version().get()))
              .orElseThrow(() -> new ConcurrentOrderUpdateException(order.id(), null));
      entity.updateState(
          order.status(), order.cancellationReason().orElse(null), order.updatedAt());
      jpa.flush();
    } catch (OptimisticLockingFailureException e) {
      throw new ConcurrentOrderUpdateException(order.id(), e);
    }
  }

  private static OrderJpaEntity toNewEntity(Order order) {
    List<OrderLineEmbeddable> lines =
        order.lines().stream()
            .map(line ->
                new OrderLineEmbeddable(
                    line.sku().value(), line.quantity(), line.unitPrice().amount()))
            .toList();
    OrderJpaEntity entity =
        new OrderJpaEntity(
            order.id().value(),
            order.customerId().value(),
            order.currency().getCurrencyCode(),
            order.total().amount(),
            order.createdAt(),
            lines);
    entity.updateState(order.status(), order.cancellationReason().orElse(null), order.updatedAt());
    return entity;
  }

  private static Order toDomain(OrderJpaEntity entity) {
    Currency currency = Currency.getInstance(entity.currency());
    List<OrderLine> lines =
        entity.lines().stream()
            .map(line ->
                new OrderLine(
                    new Sku(line.sku()), line.quantity(), new Money(line.unitPrice(), currency)))
            .toList();
    return Order.rehydrate(
        new OrderId(entity.id()),
        new CustomerId(entity.customerId()),
        lines,
        entity.status(),
        entity.cancellationReason(),
        entity.createdAt(),
        entity.updatedAt(),
        entity.version());
  }
}

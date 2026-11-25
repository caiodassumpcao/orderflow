package com.caiodassumpcao.orderflow.orders.application;

import com.caiodassumpcao.orderflow.orders.application.port.OrderEventOutbox;
import com.caiodassumpcao.orderflow.orders.application.port.OrderRepository;
import com.caiodassumpcao.orderflow.orders.application.port.UnitOfWork;
import com.caiodassumpcao.orderflow.orders.domain.Order;
import com.caiodassumpcao.orderflow.orders.domain.OrderId;
import com.caiodassumpcao.orderflow.orders.domain.OrderStatus;
import java.time.Clock;
import java.util.Objects;

/**
 * Lado dos pedidos na saga de estoque (coreografia): estoque reservado confirma o pedido, estoque
 * recusado o cancela. Respostas que chegam depois de o pedido mudar de rumo (ex.: cliente cancelou
 * enquanto o estoque era reservado) são ignoradas; o inventário devolve o estoque ao receber o
 * cancelamento.
 */
public class OrderSagaService {

  static final int MAX_REASON_LENGTH = 200;

  private final OrderRepository orders;
  private final OrderEventOutbox outbox;
  private final UnitOfWork unitOfWork;
  private final Clock clock;

  public OrderSagaService(
      OrderRepository orders, OrderEventOutbox outbox, UnitOfWork unitOfWork, Clock clock) {
    this.orders = Objects.requireNonNull(orders);
    this.outbox = Objects.requireNonNull(outbox);
    this.unitOfWork = Objects.requireNonNull(unitOfWork);
    this.clock = Objects.requireNonNull(clock);
  }

  /**
   * Estoque reservado: o pedido passa por {@code STOCK_RESERVED} e é confirmado.
   *
   * @return {@code false} se o pedido já tinha saído de {@code CREATED} e a resposta foi ignorada
   * @throws OrderNotFoundException se o pedido não existe (inconsistência; vai para a DLQ)
   */
  public boolean onStockReserved(OrderId orderId) {
    return unitOfWork.inTransaction(() -> {
      Order order = load(orderId);
      if (order.status() != OrderStatus.CREATED) {
        return false;
      }
      order.markStockReserved(clock.instant());
      order.confirm(clock.instant());
      save(order);
      return true;
    });
  }

  /**
   * Estoque recusado: o pedido é cancelado com o motivo.
   *
   * @return {@code false} se o pedido já não podia ser cancelado e a resposta foi ignorada
   * @throws OrderNotFoundException se o pedido não existe (inconsistência; vai para a DLQ)
   */
  public boolean onStockRejected(OrderId orderId, String reason) {
    return unitOfWork.inTransaction(() -> {
      Order order = load(orderId);
      if (order.status() != OrderStatus.CREATED) {
        return false;
      }
      order.cancel(truncate(reason), clock.instant());
      save(order);
      return true;
    });
  }

  private Order load(OrderId orderId) {
    return orders.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
  }

  private void save(Order order) {
    var events = order.pullEvents();
    orders.save(order);
    outbox.append(events);
  }

  private static String truncate(String reason) {
    if (reason == null || reason.length() <= MAX_REASON_LENGTH) {
      return reason;
    }
    return reason.substring(0, MAX_REASON_LENGTH - 1) + "…";
  }
}

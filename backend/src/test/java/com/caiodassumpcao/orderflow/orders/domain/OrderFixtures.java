package com.caiodassumpcao.orderflow.orders.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

/** Dados de teste do domínio de pedidos. */
public final class OrderFixtures {

  public static final Currency BRL = Currency.getInstance("BRL");
  public static final Currency USD = Currency.getInstance("USD");
  public static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");
  public static final CustomerId CUSTOMER = new CustomerId("customer-1");

  private OrderFixtures() {}

  public static Money brl(String amount) {
    return new Money(new BigDecimal(amount), BRL);
  }

  public static OrderLine line(String sku, int quantity, String unitPrice) {
    return new OrderLine(new Sku(sku), quantity, brl(unitPrice));
  }

  public static OrderId newOrderId() {
    return new OrderId(UUID.randomUUID());
  }

  /** Pedido criado e já salvo (versão 0), com dois itens (total 250.00 BRL). */
  public static Order placedOrder() {
    return Order.rehydrate(
        newOrderId(),
        CUSTOMER,
        List.of(line("SKU-001", 2, "50.00"), line("SKU-002", 1, "150.00")),
        OrderStatus.CREATED,
        null,
        NOW,
        NOW,
        0L);
  }

  /** Simula salvar e recarregar o pedido com a versão informada. */
  public static Order reload(Order order, long version) {
    return Order.rehydrate(
        order.id(),
        order.customerId(),
        order.lines(),
        order.status(),
        order.cancellationReason().orElse(null),
        order.createdAt(),
        order.updatedAt(),
        version);
  }
}

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

  /** Pedido recém-criado com dois itens (total 250.00 BRL) e eventos já consumidos. */
  public static Order placedOrder() {
    Order order =
        Order.place(
            newOrderId(),
            CUSTOMER,
            List.of(line("SKU-001", 2, "50.00"), line("SKU-002", 1, "150.00")),
            NOW);
    order.pullEvents();
    return order;
  }
}

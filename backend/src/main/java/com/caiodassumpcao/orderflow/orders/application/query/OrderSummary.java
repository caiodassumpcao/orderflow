package com.caiodassumpcao.orderflow.orders.application.query;

import com.caiodassumpcao.orderflow.orders.domain.OrderId;
import com.caiodassumpcao.orderflow.orders.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

/** Linha da listagem de pedidos (modelo de leitura). */
public record OrderSummary(
    OrderId id,
    OrderStatus status,
    Currency currency,
    BigDecimal total,
    int itemCount,
    Instant createdAt,
    Instant updatedAt) {}

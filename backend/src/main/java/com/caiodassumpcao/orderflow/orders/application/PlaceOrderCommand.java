package com.caiodassumpcao.orderflow.orders.application;

import com.caiodassumpcao.orderflow.orders.domain.CustomerId;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Pedido de criação.
 *
 * @param customerId cliente autenticado
 * @param idempotencyKey chave enviada no header {@code Idempotency-Key}
 * @param currency código ISO 4217 da moeda do pedido
 * @param items itens na ordem enviada
 */
public record PlaceOrderCommand(
    CustomerId customerId, String idempotencyKey, String currency, List<Item> items) {

  public PlaceOrderCommand {
    Objects.requireNonNull(customerId, "customer id is required");
    Objects.requireNonNull(idempotencyKey, "idempotency key is required");
    // Cópia que preserva itens nulos: o serviço os rejeita como pedido inválido.
    items = items == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(items));
  }

  /** Item da requisição. */
  public record Item(String sku, int quantity, BigDecimal unitPrice) {}
}

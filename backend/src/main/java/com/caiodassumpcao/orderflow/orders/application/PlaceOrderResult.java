package com.caiodassumpcao.orderflow.orders.application;

import com.caiodassumpcao.orderflow.orders.domain.Order;

/**
 * Resultado da criação.
 *
 * @param order pedido criado (ou o criado pela requisição original, em um replay)
 * @param replayed {@code true} quando a chave de idempotência já tinha sido usada com o mesmo
 *     conteúdo e nenhum pedido novo foi criado
 */
public record PlaceOrderResult(Order order, boolean replayed) {}

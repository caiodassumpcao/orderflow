package com.caiodassumpcao.orderflow.orders.application.port;

import com.caiodassumpcao.orderflow.orders.domain.CustomerId;
import com.caiodassumpcao.orderflow.orders.domain.OrderId;
import java.util.Optional;

/** Registro das chaves de idempotência da criação de pedidos, por cliente. */
public interface IdempotencyKeyStore {

  Optional<StoredKey> find(CustomerId customerId, String key);

  /**
   * Registra a chave. Se outra transação já registrou a mesma chave para o cliente, falha com
   * {@link IdempotencyKeyTakenException}; a transação corrente deve ser descartada.
   */
  void save(CustomerId customerId, String key, String requestHash, OrderId orderId);

  /** Chave já usada: o hash da requisição original e o pedido criado por ela. */
  record StoredKey(String requestHash, OrderId orderId) {}
}

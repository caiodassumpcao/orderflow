package com.caiodassumpcao.orderflow.orders.adapter.persistence;

import com.caiodassumpcao.orderflow.orders.application.port.IdempotencyKeyStore;
import com.caiodassumpcao.orderflow.orders.application.port.IdempotencyKeyTakenException;
import com.caiodassumpcao.orderflow.orders.domain.CustomerId;
import com.caiodassumpcao.orderflow.orders.domain.OrderId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chaves de idempotência em {@code command.idempotency_keys}. A chave primária (cliente, chave)
 * serializa requisições concorrentes: a segunda espera a primeira terminar e então falha.
 */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
class JdbcIdempotencyKeyStore implements IdempotencyKeyStore {

  private final JdbcClient jdbc;

  JdbcIdempotencyKeyStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<StoredKey> find(CustomerId customerId, String key) {
    return jdbc.sql(
            """
            SELECT request_hash, order_id
              FROM command.idempotency_keys
             WHERE customer_id = :customerId AND idempotency_key = :key
            """)
        .param("customerId", customerId.value())
        .param("key", key)
        .query((rs, row) ->
            new StoredKey(
                rs.getString("request_hash"), new OrderId(rs.getObject("order_id", UUID.class))))
        .optional();
  }

  @Override
  public void save(CustomerId customerId, String key, String requestHash, OrderId orderId) {
    try {
      jdbc.sql(
              """
              INSERT INTO command.idempotency_keys
                  (customer_id, idempotency_key, request_hash, order_id)
              VALUES (:customerId, :key, :requestHash, :orderId)
              """)
          .param("customerId", customerId.value())
          .param("key", key)
          .param("requestHash", requestHash)
          .param("orderId", orderId.value())
          .update();
    } catch (DuplicateKeyException e) {
      throw new IdempotencyKeyTakenException(e);
    }
  }
}

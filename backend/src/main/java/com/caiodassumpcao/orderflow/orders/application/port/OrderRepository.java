package com.caiodassumpcao.orderflow.orders.application.port;

import com.caiodassumpcao.orderflow.orders.domain.Order;
import com.caiodassumpcao.orderflow.orders.domain.OrderId;
import java.util.Optional;

/** Persistência do agregado {@link Order} no modelo de escrita. */
public interface OrderRepository {

  Optional<Order> findById(OrderId id);

  /**
   * Insere um pedido novo ou atualiza um existente. Atualizações usam controle otimista pela versão
   * lida; uma versão desatualizada falha com {@link ConcurrentOrderUpdateException}.
   */
  void save(Order order);
}

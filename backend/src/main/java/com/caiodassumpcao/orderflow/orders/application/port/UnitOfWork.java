package com.caiodassumpcao.orderflow.orders.application.port;

import java.util.function.Supplier;

/**
 * Fronteira transacional. Tudo o que roda em {@code work} é confirmado junto ou desfeito junto se
 * uma exceção escapar.
 */
public interface UnitOfWork {

  <T> T inTransaction(Supplier<T> work);
}

package com.caiodassumpcao.orderflow.inventory.application.port;

import java.util.function.Supplier;

/** Fronteira transacional do inventário: tudo em {@code work} é confirmado ou desfeito junto. */
public interface InventoryUnitOfWork {

  <T> T inTransaction(Supplier<T> work);
}

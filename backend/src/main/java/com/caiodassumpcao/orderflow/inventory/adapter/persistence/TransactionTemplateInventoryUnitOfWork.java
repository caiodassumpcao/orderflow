package com.caiodassumpcao.orderflow.inventory.adapter.persistence;

import com.caiodassumpcao.orderflow.inventory.application.port.InventoryUnitOfWork;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** {@link InventoryUnitOfWork} sobre o gerenciador de transações do Spring. */
@Component
class TransactionTemplateInventoryUnitOfWork implements InventoryUnitOfWork {

  private final TransactionTemplate transactions;

  TransactionTemplateInventoryUnitOfWork(PlatformTransactionManager transactionManager) {
    this.transactions = new TransactionTemplate(transactionManager);
  }

  @Override
  public <T> T inTransaction(Supplier<T> work) {
    return transactions.execute(status -> work.get());
  }
}

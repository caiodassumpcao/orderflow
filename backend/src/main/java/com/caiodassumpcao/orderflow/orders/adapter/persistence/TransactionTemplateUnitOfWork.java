package com.caiodassumpcao.orderflow.orders.adapter.persistence;

import com.caiodassumpcao.orderflow.orders.application.port.UnitOfWork;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** {@link UnitOfWork} sobre o gerenciador de transações do Spring (JPA + JDBC na mesma conexão). */
@Component
class TransactionTemplateUnitOfWork implements UnitOfWork {

  private final TransactionTemplate transactions;

  TransactionTemplateUnitOfWork(PlatformTransactionManager transactionManager) {
    this.transactions = new TransactionTemplate(transactionManager);
  }

  @Override
  public <T> T inTransaction(Supplier<T> work) {
    return transactions.execute(status -> work.get());
  }
}

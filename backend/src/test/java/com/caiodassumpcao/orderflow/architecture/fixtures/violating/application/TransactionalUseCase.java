package com.caiodassumpcao.orderflow.architecture.fixtures.violating.application;

import org.springframework.transaction.annotation.Transactional;

/**
 * Fixture de teste: viola de propósito a regra que mantém a camada de aplicação livre de
 * frameworks. Sem anotações de estereótipo, para não ser registrada pelo component scan.
 */
public class TransactionalUseCase {

  @Transactional
  public void execute() {
    // vazio de propósito
  }
}

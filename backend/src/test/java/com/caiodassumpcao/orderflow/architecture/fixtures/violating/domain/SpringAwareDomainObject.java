package com.caiodassumpcao.orderflow.architecture.fixtures.violating.domain;

import org.springframework.util.StringUtils;

/**
 * Fixture de teste: viola de propósito a regra de pureza do domínio. Sem anotações de estereótipo,
 * para não ser registrada pelo component scan dos testes de integração.
 */
public class SpringAwareDomainObject {

  public boolean isNamed(String name) {
    return StringUtils.hasText(name);
  }
}

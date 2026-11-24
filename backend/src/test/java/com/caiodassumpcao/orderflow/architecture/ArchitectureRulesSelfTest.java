package com.caiodassumpcao.orderflow.architecture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.caiodassumpcao.orderflow.architecture.fixtures.violating.application.TransactionalUseCase;
import com.caiodassumpcao.orderflow.architecture.fixtures.violating.domain.SpringAwareDomainObject;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/** Garante que as regras não passam "no vazio": cada fixture viola uma regra de propósito. */
class ArchitectureRulesSelfTest {

  @Test
  void domainRuleRejectsSpringDependency() {
    JavaClasses violating = new ClassFileImporter().importClasses(SpringAwareDomainObject.class);

    assertThatThrownBy(() -> ArchitectureRules.DOMAIN_IS_FRAMEWORK_FREE.check(violating))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("domain must not depend on Spring, JPA or Hibernate");
  }

  @Test
  void applicationRuleRejectsSpringDependency() {
    JavaClasses violating = new ClassFileImporter().importClasses(TransactionalUseCase.class);

    assertThatThrownBy(() -> ArchitectureRules.APPLICATION_IS_FRAMEWORK_FREE.check(violating))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("application must not depend on Spring, JPA or Hibernate");
  }
}

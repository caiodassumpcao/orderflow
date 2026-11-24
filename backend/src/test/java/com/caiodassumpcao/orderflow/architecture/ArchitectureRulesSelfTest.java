package com.caiodassumpcao.orderflow.architecture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.caiodassumpcao.orderflow.architecture.fixtures.violating.domain.SpringAwareDomainObject;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/** Garante que as regras não passam "no vazio": uma classe de domínio com Spring deve falhar. */
class ArchitectureRulesSelfTest {

  @Test
  void domainRuleRejectsSpringDependency() {
    JavaClasses violating = new ClassFileImporter().importClasses(SpringAwareDomainObject.class);

    assertThatThrownBy(() -> ArchitectureRules.DOMAIN_IS_FRAMEWORK_FREE.check(violating))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("domain must not depend on Spring, JPA or Hibernate");
  }
}

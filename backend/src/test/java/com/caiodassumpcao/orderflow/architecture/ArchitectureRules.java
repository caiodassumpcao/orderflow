package com.caiodassumpcao.orderflow.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.lang.ArchRule;

/**
 * Regras da arquitetura hexagonal. {@code allowEmptyShould(true)} é necessário enquanto os pacotes
 * de domínio/aplicação ainda não têm classes (Fase 0); {@code ArchitectureRulesSelfTest} prova que
 * as regras detectam violações reais.
 */
final class ArchitectureRules {

  static final ArchRule DOMAIN_IS_FRAMEWORK_FREE =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "org.hibernate..")
          .as("domain must not depend on Spring, JPA or Hibernate")
          .allowEmptyShould(true);

  static final ArchRule DOMAIN_DOES_NOT_DEPEND_ON_OUTER_LAYERS =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..application..", "..adapter..")
          .as("domain must not depend on application or adapters")
          .allowEmptyShould(true);

  static final ArchRule APPLICATION_DOES_NOT_DEPEND_ON_ADAPTERS =
      noClasses()
          .that()
          .resideInAPackage("..application..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..adapter..")
          .as("application must not depend on adapters")
          .allowEmptyShould(true);

  private ArchitectureRules() {}
}

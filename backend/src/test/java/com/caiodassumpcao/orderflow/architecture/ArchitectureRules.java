package com.caiodassumpcao.orderflow.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.lang.ArchRule;

/**
 * Regras da arquitetura hexagonal. {@code allowEmptyShould(true)} mantém as regras válidas para
 * contextos que ainda não têm classes (ex.: {@code inventory} antes da Fase 4); {@code
 * ArchitectureRulesSelfTest} prova que cada regra detecta violações reais.
 */
final class ArchitectureRules {

  private static final String[] FRAMEWORK_PACKAGES = {
    "org.springframework..", "jakarta.persistence..", "org.hibernate.."
  };

  static final ArchRule DOMAIN_IS_FRAMEWORK_FREE =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(FRAMEWORK_PACKAGES)
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

  static final ArchRule APPLICATION_IS_FRAMEWORK_FREE =
      noClasses()
          .that()
          .resideInAPackage("..application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(FRAMEWORK_PACKAGES)
          .as("application must not depend on Spring, JPA or Hibernate")
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

  static final ArchRule ORDERS_DO_NOT_DEPEND_ON_INVENTORY =
      noClasses()
          .that()
          .resideInAPackage("com.caiodassumpcao.orderflow.orders..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.caiodassumpcao.orderflow.inventory..")
          .as("orders must talk to inventory only through events")
          .allowEmptyShould(true);

  static final ArchRule INVENTORY_DOES_NOT_DEPEND_ON_ORDERS =
      noClasses()
          .that()
          .resideInAPackage("com.caiodassumpcao.orderflow.inventory..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.caiodassumpcao.orderflow.orders..")
          .as("inventory must talk to orders only through events")
          .allowEmptyShould(true);

  private ArchitectureRules() {}
}

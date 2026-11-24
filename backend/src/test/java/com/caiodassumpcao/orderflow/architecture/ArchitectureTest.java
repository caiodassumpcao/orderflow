package com.caiodassumpcao.orderflow.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;

@AnalyzeClasses(
    packages = "com.caiodassumpcao.orderflow",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest
  void domainIsFrameworkFree(JavaClasses classes) {
    ArchitectureRules.DOMAIN_IS_FRAMEWORK_FREE.check(classes);
  }

  @ArchTest
  void domainDoesNotDependOnOuterLayers(JavaClasses classes) {
    ArchitectureRules.DOMAIN_DOES_NOT_DEPEND_ON_OUTER_LAYERS.check(classes);
  }

  @ArchTest
  void applicationDoesNotDependOnAdapters(JavaClasses classes) {
    ArchitectureRules.APPLICATION_DOES_NOT_DEPEND_ON_ADAPTERS.check(classes);
  }
}

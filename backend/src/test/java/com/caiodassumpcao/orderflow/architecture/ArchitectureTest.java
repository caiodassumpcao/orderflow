package com.caiodassumpcao.orderflow.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "com.caiodassumpcao.orderflow",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest
  static final ArchRule domainIsFrameworkFree = ArchitectureRules.DOMAIN_IS_FRAMEWORK_FREE;

  @ArchTest
  static final ArchRule domainDoesNotDependOnOuterLayers =
      ArchitectureRules.DOMAIN_DOES_NOT_DEPEND_ON_OUTER_LAYERS;

  @ArchTest
  static final ArchRule applicationDoesNotDependOnAdapters =
      ArchitectureRules.APPLICATION_DOES_NOT_DEPEND_ON_ADAPTERS;
}

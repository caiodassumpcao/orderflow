package com.caiodassumpcao.orderflow.architecture.fixtures.violating.web;

import org.springframework.web.bind.annotation.GetMapping;

/**
 * Fixture de teste: endpoint sem {@code @PreAuthorize}. Sem estereótipo, fora do component scan.
 */
public class UnguardedController {

  @GetMapping("/fixture")
  public String unguarded() {
    return "open";
  }
}

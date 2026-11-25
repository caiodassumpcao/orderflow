package com.caiodassumpcao.orderflow.shared.security;

/**
 * Papéis do realm do Keycloak usados na autorização. As expressões {@code @PreAuthorize} dos
 * controllers usam as constantes {@code HAS_*}.
 */
public final class Roles {

  /** Cliente: cria, cancela e consulta os próprios pedidos. */
  public static final String USER = "USER";

  /** Operação: envia pedidos, consulta estoque e todos os pedidos. */
  public static final String ADMIN = "ADMIN";

  public static final String HAS_USER = "hasRole('" + USER + "')";
  public static final String HAS_ADMIN = "hasRole('" + ADMIN + "')";

  private Roles() {}
}

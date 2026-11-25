package com.caiodassumpcao.orderflow;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.caiodassumpcao.orderflow.shared.security.Roles;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

/**
 * Tokens de teste com os papéis do Keycloak, no claim {@code realm_access.roles} e como authorities
 * (o post-processor de teste não passa pelo conversor da aplicação).
 */
public final class TestTokens {

  private TestTokens() {}

  /** Cliente comum (papel USER). */
  public static JwtRequestPostProcessor customer(String subject) {
    return withRoles(subject, Roles.USER);
  }

  /** Operação (papéis USER e ADMIN, como o dev-admin do realm). */
  public static JwtRequestPostProcessor admin(String subject) {
    return withRoles(subject, Roles.USER, Roles.ADMIN);
  }

  /** Autenticado, mas sem nenhum papel da aplicação. */
  public static JwtRequestPostProcessor withoutRoles(String subject) {
    return withRoles(subject);
  }

  private static JwtRequestPostProcessor withRoles(String subject, String... roles) {
    List<String> roleNames = List.of(roles);
    return jwt()
        .jwt(token ->
            token
                .subject(subject)
                .claim("azp", "orderflow-spa")
                .claim("realm_access", Map.of("roles", roleNames)))
        .authorities(
            Arrays.stream(roles)
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList());
  }
}

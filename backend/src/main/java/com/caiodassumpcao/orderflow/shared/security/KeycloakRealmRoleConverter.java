package com.caiodassumpcao.orderflow.shared.security;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Converte os papéis do realm do Keycloak ({@code realm_access.roles}) em authorities {@code
 * ROLE_*}: {@code USER} vira {@code ROLE_USER}. Papéis com nomes fora do padrão são ignorados.
 */
public final class KeycloakRealmRoleConverter
    implements Converter<Jwt, Collection<GrantedAuthority>> {

  static final String REALM_ACCESS_CLAIM = "realm_access";
  static final String ROLES = "roles";
  private static final Pattern ROLE_NAME = Pattern.compile("^[A-Za-z][A-Za-z0-9_-]{0,63}$");

  @Override
  public Collection<GrantedAuthority> convert(Jwt jwt) {
    if (!(jwt.getClaim(REALM_ACCESS_CLAIM) instanceof Map<?, ?> realmAccess)
        || !(realmAccess.get(ROLES) instanceof List<?> roles)) {
      return List.of();
    }
    return roles.stream()
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .filter(role -> ROLE_NAME.matcher(role).matches())
        .<GrantedAuthority>map(
            role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase(Locale.ROOT)))
        .distinct()
        .toList();
  }
}

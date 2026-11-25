package com.caiodassumpcao.orderflow.shared.security;

import java.util.Set;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Aceita só tokens emitidos para os clientes autorizados (claim {@code azp} do Keycloak). Sem isso,
 * um token obtido por qualquer outro cliente do mesmo realm seria aceito pela API.
 */
public final class AuthorizedPartyValidator implements OAuth2TokenValidator<Jwt> {

  static final String AUTHORIZED_PARTY_CLAIM = "azp";

  private final Set<String> allowed;

  public AuthorizedPartyValidator(Set<String> allowed) {
    if (allowed == null || allowed.isEmpty()) {
      throw new IllegalArgumentException("At least one authorized party is required");
    }
    this.allowed = Set.copyOf(allowed);
  }

  @Override
  public OAuth2TokenValidatorResult validate(Jwt jwt) {
    String party = jwt.getClaimAsString(AUTHORIZED_PARTY_CLAIM);
    if (party != null && allowed.contains(party)) {
      return OAuth2TokenValidatorResult.success();
    }
    return OAuth2TokenValidatorResult.failure(
        new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN, "The token was not issued to an allowed client", null));
  }
}

package com.caiodassumpcao.orderflow.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class SecurityComponentsTest {

  private static Jwt jwt(Map<String, Object> claims) {
    Jwt.Builder builder =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("user-1")
            .issuedAt(Instant.EPOCH);
    claims.forEach(builder::claim);
    return builder.build();
  }

  @Nested
  class RealmRoles {

    private final KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter();

    @Test
    void turnsRealmRolesIntoSpringRoles() {
      Jwt token = jwt(Map.of("realm_access", Map.of("roles", List.of("USER", "admin", "USER"))));

      assertThat(converter.convert(token))
          .extracting(GrantedAuthority::getAuthority)
          .containsExactly("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void ignoresMissingOrMalformedClaims() {
      assertThat(converter.convert(jwt(Map.of("scope", "openid")))).isEmpty();
      assertThat(converter.convert(jwt(Map.of("realm_access", "USER")))).isEmpty();
      assertThat(converter.convert(jwt(Map.of("realm_access", Map.of("roles", "USER"))))).isEmpty();
      assertThat(
              converter.convert(
                  jwt(
                      Map.of(
                          "realm_access",
                          Map.of("roles", List.of("ok-role", "bad role", 42, "x".repeat(65)))))))
          .extracting(GrantedAuthority::getAuthority)
          .containsExactly("ROLE_OK-ROLE");
    }

    @Test
    void wiresTheConverterIntoTheAuthentication() {
      Jwt token = jwt(Map.of("realm_access", Map.of("roles", List.of("ADMIN"))));

      assertThat(SecurityConfig.jwtAuthenticationConverter().convert(token))
          .satisfies(auth -> {
            assertThat(auth.getName()).isEqualTo("user-1");
            assertThat(auth.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                // O Spring Security 7 também acrescenta FACTOR_BEARER (fator de autenticação).
                .contains("ROLE_ADMIN")
                .doesNotContain("ROLE_USER");
          });
    }
  }

  @Nested
  class AuthorizedParty {

    private final AuthorizedPartyValidator validator =
        new AuthorizedPartyValidator(Set.of("orderflow-spa"));

    @Test
    void acceptsTokensIssuedToAnAllowedClient() {
      assertThat(validator.validate(jwt(Map.of("azp", "orderflow-spa"))).hasErrors()).isFalse();
    }

    @Test
    void rejectsTokensIssuedToOtherClientsOrWithoutAzp() {
      assertThat(validator.validate(jwt(Map.of("azp", "other-client"))).hasErrors()).isTrue();
      assertThat(validator.validate(jwt(Map.of("scope", "openid"))).getErrors())
          .singleElement()
          .satisfies(error -> assertThat(error.getErrorCode()).isEqualTo("invalid_token"));
    }

    @Test
    void requiresAtLeastOneAllowedClient() {
      assertThatThrownBy(() -> new AuthorizedPartyValidator(Set.of()))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  class RateLimiting {

    /** Relógio que só anda quando o teste manda. */
    private static final class ManualClock extends Clock {

      private Instant now = Instant.parse("2026-11-25T12:00:00Z");

      void advance(Duration duration) {
        now = now.plus(duration);
      }

      @Override
      public ZoneId getZone() {
        return ZoneOffset.UTC;
      }

      @Override
      public Clock withZone(ZoneId zone) {
        return this;
      }

      @Override
      public Instant instant() {
        return now;
      }
    }

    private final ManualClock clock = new ManualClock();
    private final TokenBucketRateLimiter limiter =
        new TokenBucketRateLimiter(3, Duration.ofMinutes(1), clock);

    @Test
    void allowsABurstUpToTheCapacityThenAsksToWait() {
      assertThat(limiter.tryConsume("alice").allowed()).isTrue();
      assertThat(limiter.tryConsume("alice").allowed()).isTrue();
      assertThat(limiter.tryConsume("alice").allowed()).isTrue();

      TokenBucketRateLimiter.Decision denied = limiter.tryConsume("alice");

      assertThat(denied.allowed()).isFalse();
      assertThat(denied.retryAfterSeconds()).isEqualTo(20);
    }

    @Test
    void refillsOverTimeAndNeverAboveTheCapacity() {
      for (int i = 0; i < 3; i++) {
        limiter.tryConsume("alice");
      }

      clock.advance(Duration.ofSeconds(20));
      assertThat(limiter.tryConsume("alice").allowed()).isTrue();
      assertThat(limiter.tryConsume("alice").allowed()).isFalse();

      clock.advance(Duration.ofHours(1));
      for (int i = 0; i < 3; i++) {
        assertThat(limiter.tryConsume("alice").allowed()).isTrue();
      }
      assertThat(limiter.tryConsume("alice").allowed()).isFalse();
    }

    @Test
    void keepsAnIndependentBucketPerUser() {
      for (int i = 0; i < 3; i++) {
        limiter.tryConsume("alice");
      }

      assertThat(limiter.tryConsume("alice").allowed()).isFalse();
      assertThat(limiter.tryConsume("bob").allowed()).isTrue();
    }

    @Test
    void forgetsIdleUsersOnceTooManyAreTracked() {
      for (int i = 0; i <= TokenBucketRateLimiter.MAX_KEYS; i++) {
        limiter.tryConsume("user-" + i);
      }
      clock.advance(Duration.ofMinutes(5));

      limiter.tryConsume("newcomer");

      assertThat(limiter.trackedKeys()).isLessThan(10);
    }

    @Test
    void rejectsInvalidConfiguration() {
      assertThatThrownBy(() -> new TokenBucketRateLimiter(0, Duration.ofMinutes(1), clock))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> new TokenBucketRateLimiter(1, Duration.ZERO, clock))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void roundsTheWaitUpToWholeSeconds() {
      assertThat(
              new TokenBucketRateLimiter.Decision(false, Duration.ofMillis(1)).retryAfterSeconds())
          .isEqualTo(1);
      assertThat(
              new TokenBucketRateLimiter.Decision(false, Duration.ofMillis(2500))
                  .retryAfterSeconds())
          .isEqualTo(3);
    }
  }
}

package com.caiodassumpcao.orderflow.shared.security;

import com.caiodassumpcao.orderflow.shared.observability.CorrelationIdFilter;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Segurança HTTP: API stateless protegida por JWT (OAuth2 Resource Server), papéis do Keycloak
 * (RBAC), CORS restrito, limite de comandos e headers de segurança. Tudo que não for explicitamente
 * liberado exige autenticação; cada endpoint declara o papel exigido com {@code @PreAuthorize}.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  /** API pura: não carrega nada, não pode ser embutida em frames. */
  static final String API_CSP = "default-src 'none'; frame-ancestors 'none'; base-uri 'none'";

  /** Swagger UI (apenas dev) precisa carregar scripts, estilos e imagens da própria origem. */
  static final String DEV_CSP =
      "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
          + "img-src 'self' data:; frame-ancestors 'none'; base-uri 'self'; form-action 'self'";

  /** Probes de orquestração: públicos em qualquer perfil, sem detalhes (show-details: never). */
  static final String[] PUBLIC_PROBES = {"/actuator/health/liveness", "/actuator/health/readiness"};

  private static final String[] DEV_PUBLIC_ENDPOINTS = {
    "/actuator/health",
    "/actuator/health/**",
    "/swagger-ui.html",
    "/swagger-ui/**",
    "/v3/api-docs",
    "/v3/api-docs/**"
  };

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      OrderflowSecurityProperties properties,
      ProblemDetailsAuthenticationEntryPoint authenticationEntryPoint,
      ProblemDetailsAccessDeniedHandler accessDeniedHandler,
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver,
      Clock clock)
      throws Exception {
    boolean devEndpointsPublic = properties.devEndpointsPublic();

    http.csrf(AbstractHttpConfigurer::disable) // stateless + bearer token: sem cookie de sessão
        .cors(Customizer.withDefaults())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .headers(headers ->
            headers
                .contentSecurityPolicy(
                    csp -> csp.policyDirectives(devEndpointsPublic ? DEV_CSP : API_CSP))
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                .frameOptions(frame -> frame.deny())
                .httpStrictTransportSecurity(
                    hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000))
                .addHeaderWriter(
                    new StaticHeadersWriter(
                        "Permissions-Policy", "camera=(), microphone=(), geolocation=()")))
        .authorizeHttpRequests(auth -> {
          auth.requestMatchers(HttpMethod.GET, PUBLIC_PROBES).permitAll();
          if (devEndpointsPublic) {
            auth.requestMatchers(DEV_PUBLIC_ENDPOINTS).permitAll();
          }
          auth.requestMatchers("/api/v1/admin/**")
              .hasRole(Roles.ADMIN)
              .requestMatchers("/api/**")
              .authenticated()
              .anyRequest()
              .authenticated();
        })
        .oauth2ResourceServer(oauth2 ->
            oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler))
        .exceptionHandling(exceptions ->
            exceptions
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler));

    OrderflowSecurityProperties.RateLimit rateLimit = properties.rateLimit();
    if (rateLimit.enabled()) {
      http.addFilterAfter(
          new WriteRateLimitFilter(
              new TokenBucketRateLimiter(rateLimit.capacity(), rateLimit.refillPeriod(), clock),
              resolver),
          BearerTokenAuthenticationFilter.class);
    }
    return http.build();
  }

  /**
   * Papéis do realm ({@code realm_access.roles}) viram {@code ROLE_*}; o principal é o {@code sub}.
   */
  static JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
    return converter;
  }

  /**
   * Decoder resolvido sob demanda (a API sobe mesmo com o Keycloak fora do ar) que, além de
   * assinatura, expiração e emissor, exige que o token tenha sido emitido para um cliente
   * autorizado ({@code azp}).
   */
  @Bean
  JwtDecoder jwtDecoder(
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
      OrderflowSecurityProperties properties) {
    AuthorizedPartyValidator authorizedParty =
        new AuthorizedPartyValidator(new HashSet<>(properties.jwt().authorizedParties()));
    return new SupplierJwtDecoder(() -> {
      NimbusJwtDecoder decoder = (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(issuerUri);
      decoder.setJwtValidator(
          new DelegatingOAuth2TokenValidator<>(
              JwtValidators.createDefaultWithIssuer(issuerUri), authorizedParty));
      return decoder;
    });
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(OrderflowSecurityProperties properties) {
    CorsConfiguration cors = new CorsConfiguration();
    cors.setAllowedOrigins(properties.cors().allowedOrigins());
    cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    cors.setAllowedHeaders(
        List.of(
            HttpHeaders.AUTHORIZATION,
            HttpHeaders.CONTENT_TYPE,
            HttpHeaders.IF_MATCH,
            "Idempotency-Key",
            CorrelationIdFilter.HEADER));
    // A SPA precisa ler estes headers das respostas (criação, replay, limite de comandos e versão
    // do produto para o If-Match).
    cors.setExposedHeaders(
        List.of(
            CorrelationIdFilter.HEADER,
            HttpHeaders.LOCATION,
            HttpHeaders.RETRY_AFTER,
            HttpHeaders.ETAG,
            "Idempotency-Replayed"));
    cors.setAllowCredentials(false);
    cors.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", cors);
    return source;
  }
}

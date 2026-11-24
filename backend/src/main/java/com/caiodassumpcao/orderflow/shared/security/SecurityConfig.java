package com.caiodassumpcao.orderflow.shared.security;

import com.caiodassumpcao.orderflow.shared.observability.CorrelationIdFilter;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Segurança HTTP: API stateless protegida por JWT (OAuth2 Resource Server), CORS restrito e
 * headers de segurança. Tudo que não for explicitamente liberado exige autenticação.
 */
@Configuration
public class SecurityConfig {

  /** API pura: não carrega nada, não pode ser embutida em frames. */
  static final String API_CSP = "default-src 'none'; frame-ancestors 'none'; base-uri 'none'";

  /** Swagger UI (apenas dev) precisa carregar scripts, estilos e imagens da própria origem. */
  static final String DEV_CSP =
      "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
          + "img-src 'self' data:; frame-ancestors 'none'; base-uri 'self'; form-action 'self'";

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
      ProblemDetailsAccessDeniedHandler accessDeniedHandler)
      throws Exception {
    boolean devEndpointsPublic = properties.devEndpointsPublic();

    http.csrf(AbstractHttpConfigurer::disable) // stateless + bearer token: sem cookie de sessão
        .cors(Customizer.withDefaults())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .headers(
            headers ->
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
        .authorizeHttpRequests(
            auth -> {
              if (devEndpointsPublic) {
                auth.requestMatchers(DEV_PUBLIC_ENDPOINTS).permitAll();
              }
              auth.requestMatchers("/api/**").authenticated().anyRequest().authenticated();
            })
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .jwt(Customizer.withDefaults())
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler));
    return http.build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(OrderflowSecurityProperties properties) {
    CorsConfiguration cors = new CorsConfiguration();
    cors.setAllowedOrigins(properties.cors().allowedOrigins());
    cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    cors.setAllowedHeaders(
        List.of("Authorization", "Content-Type", "Idempotency-Key", CorrelationIdFilter.HEADER));
    cors.setExposedHeaders(List.of(CorrelationIdFilter.HEADER));
    cors.setAllowCredentials(false);
    cors.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", cors);
    return source;
  }
}

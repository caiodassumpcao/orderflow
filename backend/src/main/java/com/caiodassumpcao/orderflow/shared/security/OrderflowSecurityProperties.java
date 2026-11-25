package com.caiodassumpcao.orderflow.shared.security;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Propriedades de segurança da aplicação.
 *
 * @param devEndpointsPublic libera health completo e Swagger sem autenticação (somente perfil dev)
 * @param cors configuração de CORS para a SPA
 * @param jwt validações extras do token
 * @param rateLimit limite de comandos por usuário
 */
@ConfigurationProperties("orderflow.security")
public record OrderflowSecurityProperties(
    boolean devEndpointsPublic,
    Cors cors,
    @DefaultValue Jwt jwt,
    @DefaultValue RateLimit rateLimit) {

  /**
   * Configuração de CORS.
   *
   * @param allowedOrigins origens permitidas (sem curingas)
   */
  public record Cors(List<String> allowedOrigins) {

    public Cors {
      allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
  }

  /**
   * Validações do JWT além de assinatura, expiração e emissor.
   *
   * @param authorizedParties clientes ({@code azp}) cujos tokens a API aceita
   */
  public record Jwt(@DefaultValue("orderflow-spa") List<String> authorizedParties) {

    public Jwt {
      authorizedParties = List.copyOf(authorizedParties);
    }
  }

  /**
   * Limite de comandos (POST, PUT, PATCH, DELETE) por usuário.
   *
   * @param enabled liga o limite
   * @param capacity requisições permitidas de uma vez (rajada)
   * @param refillPeriod tempo para repor a capacidade inteira
   */
  public record RateLimit(
      @DefaultValue("true") boolean enabled,
      @DefaultValue("30") int capacity,
      @DefaultValue("1m") Duration refillPeriod) {}

  public OrderflowSecurityProperties {
    cors = cors == null ? new Cors(List.of()) : cors;
  }
}

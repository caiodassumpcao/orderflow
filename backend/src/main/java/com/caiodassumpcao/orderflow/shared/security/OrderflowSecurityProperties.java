package com.caiodassumpcao.orderflow.shared.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades de segurança da aplicação.
 *
 * @param devEndpointsPublic libera health e Swagger sem autenticação (somente perfil dev)
 * @param cors configuração de CORS para a SPA
 */
@ConfigurationProperties("orderflow.security")
public record OrderflowSecurityProperties(boolean devEndpointsPublic, Cors cors) {

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

  public OrderflowSecurityProperties {
    cors = cors == null ? new Cors(List.of()) : cors;
  }
}

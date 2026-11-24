package com.caiodassumpcao.orderflow.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadados do contrato OpenAPI. Swagger UI só é habilitado no perfil dev. */
@Configuration
public class OpenApiConfig {

  private static final String BEARER = "bearer-jwt";

  @Bean
  OpenAPI orderflowOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("OrderFlow API")
                .version("v1")
                .description("Plataforma de pedidos event-driven com CQRS")
                .license(new License().name("MIT")))
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
        .addSecurityItem(new SecurityRequirement().addList(BEARER));
  }
}

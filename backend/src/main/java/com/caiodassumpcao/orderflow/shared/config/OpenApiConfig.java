package com.caiodassumpcao.orderflow.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
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
        // Relativo: o Swagger UI chama a própria origem, e o contrato publicado (docs/openapi.yaml)
        // não depende do host em que foi gerado.
        .servers(List.of(new Server().url("/").description("Mesma origem do contrato")))
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

  /**
   * Tags em ordem alfabética: a ordem de varredura dos controllers não pode mudar o contrato
   * publicado (docs/openapi.yaml), que é comparado byte a byte no build.
   */
  @Bean
  OpenApiCustomizer sortedTags() {
    return openApi -> {
      if (openApi.getTags() != null) {
        openApi.setTags(
            openApi.getTags().stream().sorted(Comparator.comparing(Tag::getName)).toList());
      }
    };
  }

  /**
   * Nas respostas, todo campo que não é declarado anulável é obrigatório: a API sempre o envia. Sem
   * isso o contrato deixaria tudo opcional, e um cliente não saberia em que pode confiar. Schemas
   * de corpo de requisição mantêm o {@code required} das validações (Bean Validation).
   */
  @Bean
  OpenApiCustomizer responseFieldsRequired() {
    return openApi -> {
      if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
        return;
      }
      @SuppressWarnings("rawtypes")
      Map<String, Schema> schemas = openApi.getComponents().getSchemas();
      Set<String> requestSchemas = new HashSet<>();
      if (openApi.getPaths() != null) {
        openApi.getPaths().values().stream()
            .flatMap(path -> path.readOperations().stream())
            .filter(operation -> operation.getRequestBody() != null)
            .flatMap(operation -> operation.getRequestBody().getContent().values().stream())
            .forEach(media -> collectReferences(media.getSchema(), schemas, requestSchemas));
      }
      schemas.forEach((name, schema) -> {
        if (!requestSchemas.contains(name) && schema.getProperties() != null) {
          @SuppressWarnings("unchecked")
          Map<String, Schema<?>> properties = schema.getProperties();
          schema.setRequired(
              properties.entrySet().stream()
                  .filter(property -> !isNullable(property.getValue()))
                  .map(Map.Entry::getKey)
                  .sorted()
                  .toList());
        }
      });
    };
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void collectReferences(
      Schema<?> schema, Map<String, Schema> schemas, Set<String> found) {
    if (schema == null) {
      return;
    }
    if (schema.get$ref() != null) {
      String name = schema.get$ref().substring(schema.get$ref().lastIndexOf('/') + 1);
      if (found.add(name)) {
        collectReferences(schemas.get(name), schemas, found);
      }
    }
    collectReferences(schema.getItems(), schemas, found);
    if (schema.getProperties() != null) {
      ((Map<String, Schema<?>>) (Map) schema.getProperties())
          .values()
          .forEach(property -> collectReferences(property, schemas, found));
    }
  }

  private static boolean isNullable(Schema<?> schema) {
    return Boolean.TRUE.equals(schema.getNullable())
        || (schema.getTypes() != null && schema.getTypes().contains("null"));
  }
}

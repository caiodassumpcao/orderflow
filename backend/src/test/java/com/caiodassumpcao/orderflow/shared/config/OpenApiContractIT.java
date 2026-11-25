package com.caiodassumpcao.orderflow.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.caiodassumpcao.orderflow.TestTokens;
import com.caiodassumpcao.orderflow.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * O contrato publicado em {@code docs/openapi.yaml} tem de ser o que a API expõe. Mudou um
 * endpoint? Regenere com {@code make openapi} (ou {@code OPENAPI_UPDATE=true}) e revise o diff.
 */
@SpringBootTest(
    properties = {"springdoc.api-docs.enabled=true", "springdoc.writer-with-order-by-keys=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OpenApiContractIT {

  /** Relativo ao diretório do módulo (backend/), onde o Maven roda os testes. */
  static final Path CONTRACT = Path.of("..", "docs", "openapi.yaml");

  @Autowired private MockMvc mvc;

  @Test
  void publishedContractMatchesTheApi() throws Exception {
    String generated =
        normalize(
            mvc.perform(get("/v3/api-docs.yaml").with(TestTokens.admin("contract")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));

    if (Boolean.parseBoolean(System.getenv("OPENAPI_UPDATE"))) {
      Files.writeString(CONTRACT, generated, StandardCharsets.UTF_8);
    }

    assertThat(Files.exists(CONTRACT))
        .as("docs/openapi.yaml não existe; gere com `make openapi`")
        .isTrue();
    assertThat(normalize(Files.readString(CONTRACT, StandardCharsets.UTF_8)))
        .as("docs/openapi.yaml está desatualizado; regenere com `make openapi` e revise o diff")
        .isEqualTo(generated);
  }

  /** Independe do fim de linha do checkout (CRLF no Windows) e termina com uma quebra de linha. */
  private static String normalize(String yaml) {
    return yaml.replace("\r\n", "\n").stripTrailing() + "\n";
  }
}

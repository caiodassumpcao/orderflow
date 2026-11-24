package com.caiodassumpcao.orderflow.shared.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.caiodassumpcao.orderflow.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Segurança com a configuração padrão (não-dev): tudo exige JWT. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SecurityIT {

  private static final String UUID_REGEX =
      "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";

  @Autowired private MockMvc mvc;

  @Test
  void apiWithoutTokenReturns401AsProblemDetails() throws Exception {
    mvc.perform(get("/api/v1/orders"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", containsString("Bearer")))
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.title").value("Unauthorized"))
        .andExpect(jsonPath("$.instance").value("/api/v1/orders"))
        .andExpect(jsonPath("$.correlationId").value(matchesPattern(UUID_REGEX)));
  }

  @Test
  void apiWithValidTokenPassesAuthentication() throws Exception {
    // Com token válido a requisição passa pela segurança e chega ao MVC: rota inexistente vira 404.
    mvc.perform(
            get("/api/v1/does-not-exist").with(jwt()).header("X-Correlation-Id", "test-corr-0001"))
        .andExpect(status().isNotFound())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(header().string("X-Correlation-Id", "test-corr-0001"))
        .andExpect(jsonPath("$.correlationId").value("test-corr-0001"));
  }

  @Test
  void actuatorHealthRequiresAuthenticationOutsideDev() throws Exception {
    mvc.perform(get("/actuator/health")).andExpect(status().isUnauthorized());
  }

  @Test
  void swaggerIsNotPublicOutsideDev() throws Exception {
    mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
  }

  @Test
  void responsesCarrySecurityHeaders() throws Exception {
    mvc.perform(get("/api/v1/orders"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("X-Frame-Options", "DENY"))
        .andExpect(header().string("Referrer-Policy", "no-referrer"))
        .andExpect(header().string("Content-Security-Policy", SecurityConfig.API_CSP))
        .andExpect(header().exists("Permissions-Policy"));
  }

  @Test
  void corsAllowsConfiguredOriginOnly() throws Exception {
    mvc.perform(
            options("/api/v1/orders")
                .header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));

    mvc.perform(
            options("/api/v1/orders")
                .header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isForbidden());
  }
}

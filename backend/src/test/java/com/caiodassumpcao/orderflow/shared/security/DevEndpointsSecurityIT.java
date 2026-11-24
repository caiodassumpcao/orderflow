package com.caiodassumpcao.orderflow.shared.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.caiodassumpcao.orderflow.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Com as flags do perfil dev: health e Swagger públicos, API continua protegida. */
@SpringBootTest(
    properties = {
      "orderflow.security.dev-endpoints-public=true",
      "springdoc.api-docs.enabled=true",
      "springdoc.swagger-ui.enabled=true"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class DevEndpointsSecurityIT {

  @Autowired private MockMvc mvc;

  @Test
  void healthIsPublicAndUp() throws Exception {
    mvc.perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void readinessProbeIsPublic() throws Exception {
    mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
  }

  @Test
  void openApiDocsArePublic() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.info.title").value("OrderFlow API"));
  }

  @Test
  void apiStillRequiresToken() throws Exception {
    mvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized());
  }

  @Test
  void devCspAllowsSwaggerAssetsFromSelf() throws Exception {
    mvc.perform(get("/actuator/health"))
        .andExpect(header().string("Content-Security-Policy", SecurityConfig.DEV_CSP));
  }

  @Test
  void otherActuatorEndpointsStayProtected() throws Exception {
    mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
  }
}

package com.caiodassumpcao.orderflow.shared.security;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.caiodassumpcao.orderflow.TestTokens;
import com.caiodassumpcao.orderflow.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** RBAC com os papéis do Keycloak: o que cada papel pode fazer em cada endpoint. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuthorizationIT {

  private static final String ORDER_BODY =
      """
      {"currency":"BRL","items":[{"sku":"SKU-001","quantity":1,"unitPrice":19.90}]}
      """;

  @Autowired private MockMvc mvc;

  private ResultActions perform(MockHttpServletRequestBuilder request, JwtRequestPostProcessor as)
      throws Exception {
    return mvc.perform(request.with(as));
  }

  private static MockHttpServletRequestBuilder placeOrder() {
    return post("/api/v1/orders")
        .header("Idempotency-Key", "key-" + UUID.randomUUID())
        .contentType(MediaType.APPLICATION_JSON)
        .content(ORDER_BODY);
  }

  @Nested
  class Customers {

    private final JwtRequestPostProcessor customer = TestTokens.customer("rbac-user");

    @Test
    void manageTheirOwnOrders() throws Exception {
      perform(placeOrder(), customer).andExpect(status().isCreated());
      perform(get("/api/v1/orders"), customer).andExpect(status().isOk());
    }

    @Test
    void cannotShipOrdersReadStockOrUseTheAdminView() throws Exception {
      perform(post("/api/v1/orders/{id}/ship", UUID.randomUUID()), customer)
          .andExpect(status().isForbidden())
          .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.status").value(403))
          .andExpect(jsonPath("$.correlationId").exists());
      perform(get("/api/v1/inventory/{sku}", "SKU-001"), customer)
          .andExpect(status().isForbidden());
      perform(get("/api/v1/admin/orders"), customer).andExpect(status().isForbidden());
      perform(get("/api/v1/admin/orders/{id}", UUID.randomUUID()), customer)
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  class Admins {

    private final JwtRequestPostProcessor admin = TestTokens.admin("rbac-admin");

    @Test
    void reachTheOperationsEndpoints() throws Exception {
      // 404/409 provam que a autorização passou e a requisição chegou ao caso de uso.
      perform(post("/api/v1/orders/{id}/ship", UUID.randomUUID()), admin)
          .andExpect(status().isNotFound());
      perform(get("/api/v1/inventory/{sku}", "SKU-404"), admin).andExpect(status().isNotFound());
      perform(get("/api/v1/admin/orders"), admin).andExpect(status().isOk());
    }

    @Test
    void validateTheAdminFilters() throws Exception {
      perform(get("/api/v1/admin/orders").param("customerId", "not valid!"), admin)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].field").value("customerId"));
      perform(get("/api/v1/admin/orders").param("cursor", "tampered"), admin)
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  class TokensWithoutApplicationRoles {

    private final JwtRequestPostProcessor nobody = TestTokens.withoutRoles("rbac-nobody");

    @Test
    void areAuthenticatedButMayDoNothing() throws Exception {
      perform(placeOrder(), nobody).andExpect(status().isForbidden());
      perform(get("/api/v1/orders"), nobody).andExpect(status().isForbidden());
      perform(get("/api/v1/admin/orders"), nobody).andExpect(status().isForbidden());
    }
  }

  @Nested
  class PublicSurface {

    @Test
    void exposesOnlyTheProbesWithoutAToken() throws Exception {
      mvc.perform(get("/actuator/health/liveness"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("UP"))
          .andExpect(jsonPath("$.components").doesNotExist());
      mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
      mvc.perform(get("/actuator/health")).andExpect(status().isUnauthorized());
      mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
    }

    @Test
    void letsTheSpaReadTheHeadersItNeeds() throws Exception {
      mvc.perform(
              options("/api/v1/orders")
                  .header("Origin", "http://localhost:4200")
                  .header("Access-Control-Request-Method", "POST"))
          .andExpect(status().isOk());
      mvc.perform(
              get("/api/v1/orders")
                  .with(TestTokens.customer("cors-user"))
                  .header("Origin", "http://localhost:4200"))
          .andExpect(header().string("Access-Control-Expose-Headers", containsString("Location")))
          .andExpect(
              header()
                  .string("Access-Control-Expose-Headers", containsString("Idempotency-Replayed")))
          .andExpect(
              header().string("Access-Control-Expose-Headers", containsString("Retry-After")));
    }
  }
}

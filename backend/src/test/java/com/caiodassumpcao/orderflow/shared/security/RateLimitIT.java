package com.caiodassumpcao.orderflow.shared.security;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.caiodassumpcao.orderflow.TestTokens;
import com.caiodassumpcao.orderflow.TestcontainersConfiguration;
import com.caiodassumpcao.orderflow.shared.error.Problems;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Limite de comandos por usuário com capacidade pequena para o teste. */
@SpringBootTest(
    properties = {
      "orderflow.security.rate-limit.capacity=2",
      "orderflow.security.rate-limit.refill-period=1h"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RateLimitIT {

  @Autowired private MockMvc mvc;

  private static MockHttpServletRequestBuilder placeOrder(String subject) {
    return post("/api/v1/orders")
        .with(TestTokens.customer(subject))
        .header("Idempotency-Key", "key-" + UUID.randomUUID())
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            """
            {"currency":"BRL","items":[{"sku":"SKU-001","quantity":1,"unitPrice":19.90}]}
            """);
  }

  @Test
  void answersTooManyRequestsOnceTheUserSpendsTheBurst() throws Exception {
    String user = "limited-" + UUID.randomUUID();
    mvc.perform(placeOrder(user)).andExpect(status().isCreated());
    mvc.perform(placeOrder(user)).andExpect(status().isCreated());

    mvc.perform(placeOrder(user))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", matchesPattern("^[1-9][0-9]*$")))
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(Problems.TYPE_BASE + "rate-limit-exceeded"))
        .andExpect(jsonPath("$.status").value(429))
        .andExpect(jsonPath("$.correlationId").exists());
  }

  @Test
  void limitsEachUserSeparatelyAndNeverLimitsQueries() throws Exception {
    String first = "limited-" + UUID.randomUUID();
    mvc.perform(placeOrder(first));
    mvc.perform(placeOrder(first));
    mvc.perform(placeOrder(first)).andExpect(status().isTooManyRequests());

    mvc.perform(placeOrder("limited-" + UUID.randomUUID())).andExpect(status().isCreated());
    for (int i = 0; i < 5; i++) {
      mvc.perform(get("/api/v1/orders").with(TestTokens.customer(first)))
          .andExpect(status().isOk());
    }
  }

  @Test
  void stillAnswersUnauthenticatedWritesWith401() throws Exception {
    mvc.perform(
            post("/api/v1/orders")
                .header("Idempotency-Key", "key-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnauthorized());
  }
}

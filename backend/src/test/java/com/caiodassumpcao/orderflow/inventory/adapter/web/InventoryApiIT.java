package com.caiodassumpcao.orderflow.inventory.adapter.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class InventoryApiIT {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcClient jdbc;

  @Test
  void showsPriceAndQuantitiesOfAProduct() throws Exception {
    String sku = "API-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    jdbc.sql(
            "INSERT INTO command.stock_items (sku, currency, unit_price, available, reserved)"
                + " VALUES (?, 'BRL', 49.90, 7, 3)")
        .param(sku)
        .update();

    mvc.perform(get("/api/v1/inventory/{sku}", sku).with(TestTokens.admin("ops")))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.sku").value(sku))
        .andExpect(jsonPath("$.currency").value("BRL"))
        .andExpect(jsonPath("$.unitPrice").value(49.90))
        .andExpect(jsonPath("$.available").value(7))
        .andExpect(jsonPath("$.reserved").value(3));
  }

  @Test
  void reportsUnknownProducts() throws Exception {
    mvc.perform(get("/api/v1/inventory/{sku}", "SKU-404").with(TestTokens.admin("ops")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value(Problems.TYPE_BASE + "stock-item-not-found"));
  }

  @Test
  void rejectsMalformedSkus() throws Exception {
    mvc.perform(get("/api/v1/inventory/{sku}", "bad-sku").with(TestTokens.admin("ops")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("sku"));
  }

  @Test
  void isReservedToAdmins() throws Exception {
    mvc.perform(get("/api/v1/inventory/{sku}", "SKU-001").with(TestTokens.customer("alice")))
        .andExpect(status().isForbidden())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @Test
  void requiresAuthentication() throws Exception {
    mvc.perform(get("/api/v1/inventory/{sku}", "SKU-001")).andExpect(status().isUnauthorized());
  }
}

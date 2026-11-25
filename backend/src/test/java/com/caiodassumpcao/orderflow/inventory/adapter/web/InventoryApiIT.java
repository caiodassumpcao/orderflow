package com.caiodassumpcao.orderflow.inventory.adapter.web;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class InventoryApiIT {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcClient jdbc;

  private final JwtRequestPostProcessor admin = TestTokens.admin("ops");

  private static String newSku() {
    return "API-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
  }

  private void insert(String sku, int available, int reserved) {
    jdbc.sql(
            "INSERT INTO command.stock_items (sku, name, currency, unit_price, available, reserved)"
                + " VALUES (?, 'Caderno', 'BRL', 49.90, ?, ?)")
        .params(sku, available, reserved)
        .update();
  }

  private static MockHttpServletRequestBuilder register(String body) {
    return post("/api/v1/inventory").contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private static MockHttpServletRequestBuilder restock(String sku, int quantity) {
    return post("/api/v1/inventory/{sku}/restock", sku)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"quantity\":" + quantity + "}");
  }

  @Nested
  class Reading {

    @Test
    void showsNamePriceQuantitiesAndVersionOfAProduct() throws Exception {
      String sku = newSku();
      insert(sku, 7, 3);

      mvc.perform(get("/api/v1/inventory/{sku}", sku).with(admin))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
          .andExpect(jsonPath("$.sku").value(sku))
          .andExpect(jsonPath("$.name").value("Caderno"))
          .andExpect(jsonPath("$.currency").value("BRL"))
          .andExpect(jsonPath("$.unitPrice").value(49.90))
          .andExpect(jsonPath("$.available").value(7))
          .andExpect(jsonPath("$.reserved").value(3))
          .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void listsProductsInSkuOrderWithACursor() throws Exception {
      String prefix = "PG" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
      insert(prefix + "-1", 1, 0);
      insert(prefix + "-2", 1, 0);
      insert(prefix + "-3", 1, 0);
      // O cursor é o último SKU visto: começa logo antes do prefixo para isolar os dados do teste.
      String before = prefix + "-0";

      mvc.perform(get("/api/v1/inventory").param("limit", "2").param("cursor", before).with(admin))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.items[0].sku").value(prefix + "-1"))
          .andExpect(jsonPath("$.items[1].sku").value(prefix + "-2"))
          .andExpect(jsonPath("$.nextCursor").value(prefix + "-2"));
      mvc.perform(
              get("/api/v1/inventory")
                  .param("limit", "1")
                  .param("cursor", prefix + "-2")
                  .with(admin))
          .andExpect(jsonPath("$.items[0].sku").value(prefix + "-3"));
    }

    @Test
    void reportsUnknownProducts() throws Exception {
      mvc.perform(get("/api/v1/inventory/{sku}", "SKU-404").with(admin))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.type").value(Problems.TYPE_BASE + "stock-item-not-found"));
    }

    @Test
    void rejectsMalformedSkusAndCursors() throws Exception {
      mvc.perform(get("/api/v1/inventory/{sku}", "bad-sku").with(admin))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].field").value("sku"));
      mvc.perform(get("/api/v1/inventory").param("cursor", "not a sku").with(admin))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].field").value("cursor"));
    }
  }

  @Nested
  class Registration {

    @Test
    void registersAProductAndPointsToIt() throws Exception {
      String sku = newSku();

      mvc.perform(
              register(
                      """
                      {"sku":"%s","name":"  Garrafa térmica ","currency":"BRL",
                       "unitPrice":89.90,"initialQuantity":12}
                      """
                          .formatted(sku))
                  .with(admin))
          .andExpect(status().isCreated())
          .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/inventory/" + sku))
          .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
          .andExpect(jsonPath("$.name").value("Garrafa térmica"))
          .andExpect(jsonPath("$.available").value(12))
          .andExpect(jsonPath("$.reserved").value(0));

      mvc.perform(get("/api/v1/inventory/{sku}", sku).with(admin))
          .andExpect(jsonPath("$.unitPrice").value(89.90));
    }

    @Test
    void refusesADuplicateSku() throws Exception {
      String sku = newSku();
      insert(sku, 1, 0);

      mvc.perform(
              register(
                      """
                      {"sku":"%s","name":"Outro","currency":"BRL","unitPrice":1,"initialQuantity":1}
                      """
                          .formatted(sku))
                  .with(admin))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.type").value(Problems.TYPE_BASE + "stock-item-already-exists"));
    }

    @Test
    void validatesEveryField() throws Exception {
      mvc.perform(
              register(
                      """
                      {"sku":"x","name":"a\\u0007b","currency":"real","unitPrice":0.001,
                       "initialQuantity":-1}
                      """)
                  .with(admin))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[*].field", hasItem("sku")))
          .andExpect(jsonPath("$.errors[*].field", hasItem("name")))
          .andExpect(jsonPath("$.errors[*].field", hasItem("currency")))
          .andExpect(jsonPath("$.errors[*].field", hasItem("unitPrice")))
          .andExpect(jsonPath("$.errors[*].field", hasItem("initialQuantity")));
    }
  }

  @Nested
  class Restocking {

    @Test
    void addsUnitsWhenIfMatchCarriesTheCurrentETag() throws Exception {
      String sku = newSku();
      insert(sku, 2, 1);

      mvc.perform(restock(sku, 5).header(HttpHeaders.IF_MATCH, "\"0\"").with(admin))
          .andExpect(status().isOk())
          .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
          .andExpect(jsonPath("$.available").value(7))
          .andExpect(jsonPath("$.reserved").value(1))
          .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void refusesAStaleETagAndReturnsTheCurrentOne() throws Exception {
      String sku = newSku();
      insert(sku, 2, 0);
      mvc.perform(restock(sku, 5).header(HttpHeaders.IF_MATCH, "\"0\"").with(admin))
          .andExpect(status().isOk());

      // Reenvio da mesma requisição: não repõe de novo.
      mvc.perform(restock(sku, 5).header(HttpHeaders.IF_MATCH, "\"0\"").with(admin))
          .andExpect(status().isPreconditionFailed())
          .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
          .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.type").value(Problems.TYPE_BASE + "stock-item-modified"));
      mvc.perform(get("/api/v1/inventory/{sku}", sku).with(admin))
          .andExpect(jsonPath("$.available").value(7));
    }

    @Test
    void acceptsOnlyAStrongETag() throws Exception {
      String sku = newSku();
      insert(sku, 2, 0);

      for (String ifMatch : new String[] {"*", "W/\"0\"", "0"}) {
        mvc.perform(restock(sku, 1).header(HttpHeaders.IF_MATCH, ifMatch).with(admin))
            .andExpect(status().isPreconditionFailed());
      }
    }

    @Test
    void requiresIfMatch() throws Exception {
      mvc.perform(restock(newSku(), 1).with(admin))
          .andExpect(status().isPreconditionRequired())
          .andExpect(jsonPath("$.type").value(Problems.TYPE_BASE + "precondition-required"));
    }

    @Test
    void reportsUnknownProductsAndInvalidQuantities() throws Exception {
      mvc.perform(restock("SKU-404", 1).header(HttpHeaders.IF_MATCH, "\"0\"").with(admin))
          .andExpect(status().isNotFound());
      mvc.perform(restock("SKU-404", 0).header(HttpHeaders.IF_MATCH, "\"0\"").with(admin))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].field").value("quantity"));
    }

    @Test
    void refusesToGoBeyondTheUnitCeiling() throws Exception {
      String sku = newSku();
      insert(sku, 999_999_999, 0);

      mvc.perform(restock(sku, 2).header(HttpHeaders.IF_MATCH, "\"0\"").with(admin))
          .andExpect(status().isUnprocessableContent())
          .andExpect(jsonPath("$.type").value(Problems.TYPE_BASE + "stock-limit-exceeded"));
    }
  }

  @Nested
  class Catalog {

    @Test
    void showsCustomersNamePriceAndAvailabilityButNotQuantities() throws Exception {
      String prefix = "CT" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
      insert(prefix + "-1", 3, 0);
      insert(prefix + "-2", 0, 4);

      mvc.perform(
              get("/api/v1/catalog")
                  .param("cursor", prefix + "-0")
                  .param("limit", "2")
                  .with(TestTokens.customer("shopper")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.items[0].sku").value(prefix + "-1"))
          .andExpect(jsonPath("$.items[0].name").value("Caderno"))
          .andExpect(jsonPath("$.items[0].unitPrice").value(49.90))
          .andExpect(jsonPath("$.items[0].inStock").value(true))
          .andExpect(jsonPath("$.items[1].inStock").value(false))
          .andExpect(jsonPath("$.items", everyItem(not(hasKey("available")))))
          .andExpect(jsonPath("$.items", everyItem(not(hasKey("reserved")))));
    }

    @Test
    void validatesTheCursorAndLimit() throws Exception {
      mvc.perform(get("/api/v1/catalog").param("limit", "101").with(TestTokens.customer("shopper")))
          .andExpect(status().isBadRequest());
      mvc.perform(
              get("/api/v1/catalog").param("cursor", "nope!").with(TestTokens.customer("shopper")))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].field").value("cursor"));
    }
  }

  @Nested
  class Authorization {

    @Test
    void reservesStockOperationsToAdmins() throws Exception {
      JwtRequestPostProcessor customer = TestTokens.customer("alice");

      mvc.perform(get("/api/v1/inventory/{sku}", "SKU-001").with(customer))
          .andExpect(status().isForbidden())
          .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
      mvc.perform(get("/api/v1/inventory").with(customer)).andExpect(status().isForbidden());
      mvc.perform(
              register(
                      """
                      {"sku":"SKU-X01","name":"X","currency":"BRL",
                       "unitPrice":1,"initialQuantity":1}
                      """)
                  .with(customer))
          .andExpect(status().isForbidden());
      mvc.perform(restock("SKU-001", 1).header(HttpHeaders.IF_MATCH, "\"0\"").with(customer))
          .andExpect(status().isForbidden());
    }

    @Test
    void letsAdminsReadTheCatalogBecauseTheyAreCustomersToo() throws Exception {
      mvc.perform(get("/api/v1/catalog").with(admin)).andExpect(status().isOk());
    }

    @Test
    void requiresAuthentication() throws Exception {
      mvc.perform(get("/api/v1/inventory/{sku}", "SKU-001")).andExpect(status().isUnauthorized());
      mvc.perform(get("/api/v1/catalog")).andExpect(status().isUnauthorized());
    }
  }
}

package com.caiodassumpcao.orderflow.inventory.adapter.web;

import com.caiodassumpcao.orderflow.inventory.application.InventoryQueryService;
import com.caiodassumpcao.orderflow.inventory.domain.Sku;
import com.caiodassumpcao.orderflow.inventory.domain.StockItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Consulta de estoque. Restrita ao papel ADMIN a partir da Fase 5. */
@RestController
@RequestMapping(path = "/api/v1/inventory", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Inventory", description = "Estoque e preço de catálogo")
class InventoryController {

  private final InventoryQueryService queries;

  InventoryController(InventoryQueryService queries) {
    this.queries = queries;
  }

  @GetMapping("/{sku}")
  @Operation(summary = "Preço e quantidades de um produto")
  StockItemResponse getStock(
      @PathVariable
          @Pattern(
              regexp = "^[A-Z0-9][A-Z0-9-]{2,31}$",
              message = "must have 3 to 32 uppercase letters, digits or hyphens")
          String sku) {
    return StockItemResponse.of(queries.getStock(new Sku(sku)));
  }

  /**
   * Situação do produto.
   *
   * @param available unidades livres para novos pedidos
   * @param reserved unidades reservadas para pedidos confirmados ainda não enviados
   */
  record StockItemResponse(
      String sku, String currency, BigDecimal unitPrice, int available, int reserved) {

    static StockItemResponse of(StockItem item) {
      return new StockItemResponse(
          item.sku().value(),
          item.currency().getCurrencyCode(),
          item.unitPrice(),
          item.available(),
          item.reserved());
    }
  }
}

package com.caiodassumpcao.orderflow.inventory.adapter.web;

import com.caiodassumpcao.orderflow.inventory.application.InventoryQueryService;
import com.caiodassumpcao.orderflow.inventory.application.StockPage;
import com.caiodassumpcao.orderflow.inventory.domain.Sku;
import com.caiodassumpcao.orderflow.inventory.domain.StockItem;
import com.caiodassumpcao.orderflow.shared.security.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catálogo para clientes (papel USER): nome, preço e se há estoque. As quantidades exatas ficam só
 * na visão de operação. O preço exibido é o que o inventário exige no pedido (ADR-0008).
 */
@RestController
@RequestMapping(path = "/api/v1/catalog", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Catalog", description = "Produtos à venda e preço de catálogo")
class CatalogController {

  private final InventoryQueryService queries;

  CatalogController(InventoryQueryService queries) {
    this.queries = queries;
  }

  @GetMapping
  @PreAuthorize(Roles.HAS_USER)
  @Operation(
      summary = "Lista os produtos à venda",
      description = "Em ordem de SKU; envie o nextCursor da resposta no parâmetro cursor.")
  CatalogPageResponse listProducts(
      @Parameter(description = "Itens por página (1 a 100)")
          @RequestParam(defaultValue = "" + StockPage.DEFAULT_LIMIT)
          @Min(1)
          @Max(StockPage.MAX_LIMIT)
          int limit,
      @Parameter(description = "nextCursor da página anterior")
          @RequestParam(required = false)
          @Pattern(
              regexp = InventoryController.SKU_REGEX,
              message = "must be a nextCursor value of a previous page")
          String cursor) {
    StockPage page = queries.listStock(Optional.ofNullable(cursor).map(Sku::new), limit);
    return new CatalogPageResponse(
        page.items().stream().map(CatalogItem::of).toList(),
        page.next().map(Sku::value).orElse(null));
  }

  /**
   * Página do catálogo.
   *
   * @param nextCursor valor para o parâmetro {@code cursor} da próxima página; nulo na última
   */
  record CatalogPageResponse(List<CatalogItem> items, String nextCursor) {}

  /**
   * Produto à venda.
   *
   * @param inStock há ao menos uma unidade livre agora (a reserva é decidida ao criar o pedido)
   */
  record CatalogItem(
      String sku, String name, String currency, BigDecimal unitPrice, boolean inStock) {

    static CatalogItem of(StockItem item) {
      return new CatalogItem(
          item.sku().value(),
          item.name(),
          item.currency().getCurrencyCode(),
          item.unitPrice(),
          item.available() > 0);
    }
  }
}

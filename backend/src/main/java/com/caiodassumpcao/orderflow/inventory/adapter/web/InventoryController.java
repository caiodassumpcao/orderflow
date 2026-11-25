package com.caiodassumpcao.orderflow.inventory.adapter.web;

import com.caiodassumpcao.orderflow.inventory.application.InventoryCommandService;
import com.caiodassumpcao.orderflow.inventory.application.InventoryQueryService;
import com.caiodassumpcao.orderflow.inventory.application.RegisterProductCommand;
import com.caiodassumpcao.orderflow.inventory.application.StockPage;
import com.caiodassumpcao.orderflow.inventory.domain.Sku;
import com.caiodassumpcao.orderflow.inventory.domain.StockItem;
import com.caiodassumpcao.orderflow.shared.security.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Estoque e catálogo para a operação (papel ADMIN): consulta, cadastro de produtos e reposição. A
 * reposição exige {@code If-Match} com o {@code ETag} atual do produto.
 */
@RestController
@RequestMapping(path = "/api/v1/inventory", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Inventory", description = "Estoque e preço de catálogo")
class InventoryController {

  static final String SKU_REGEX = "^[A-Z0-9][A-Z0-9-]{2,31}$";
  static final String SKU_MESSAGE = "must have 3 to 32 uppercase letters, digits or hyphens";

  private final InventoryQueryService queries;
  private final InventoryCommandService commands;

  InventoryController(InventoryQueryService queries, InventoryCommandService commands) {
    this.queries = queries;
    this.commands = commands;
  }

  @GetMapping
  @PreAuthorize(Roles.HAS_ADMIN)
  @Operation(
      summary = "Lista os produtos com preço e quantidades",
      description = "Em ordem de SKU; envie o nextCursor da resposta no parâmetro cursor.")
  StockPageResponse listStock(
      @Parameter(description = "Itens por página (1 a 100)")
          @RequestParam(defaultValue = "" + StockPage.DEFAULT_LIMIT)
          @Min(1)
          @Max(StockPage.MAX_LIMIT)
          int limit,
      @Parameter(description = "nextCursor da página anterior")
          @RequestParam(required = false)
          @Pattern(regexp = SKU_REGEX, message = "must be a nextCursor value of a previous page")
          String cursor) {
    StockPage page = queries.listStock(Optional.ofNullable(cursor).map(Sku::new), limit);
    return new StockPageResponse(
        page.items().stream().map(StockItemResponse::of).toList(),
        page.next().map(Sku::value).orElse(null));
  }

  @GetMapping("/{sku}")
  @PreAuthorize(Roles.HAS_ADMIN)
  @Operation(summary = "Preço e quantidades de um produto (com ETag)")
  ResponseEntity<StockItemResponse> getStock(
      @PathVariable @Pattern(regexp = SKU_REGEX, message = SKU_MESSAGE) String sku) {
    return withETag(ResponseEntity.ok(), queries.getStock(new Sku(sku)));
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize(Roles.HAS_ADMIN)
  @Operation(summary = "Cadastra um produto", description = "SKU já cadastrado responde 409.")
  @ApiResponse(responseCode = "201", description = "Produto cadastrado")
  ResponseEntity<StockItemResponse> registerProduct(
      @Valid @RequestBody RegisterProductRequest request) {
    StockItem item =
        commands.registerProduct(
            new RegisterProductCommand(
                new Sku(request.sku()),
                request.name(),
                Currency.getInstance(request.currency()),
                request.unitPrice(),
                request.initialQuantity()));
    return withETag(
        ResponseEntity.created(URI.create("/api/v1/inventory/" + item.sku().value())), item);
  }

  @PostMapping(path = "/{sku}/restock", consumes = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize(Roles.HAS_ADMIN)
  @Operation(
      summary = "Repõe unidades livres de um produto",
      description =
          "Exige If-Match com o ETag atual do produto: sem o header responde 428; se o produto"
              + " mudou (inclusive por reservas de pedidos), 412. Assim um reenvio da mesma"
              + " requisição não repõe o estoque duas vezes.")
  ResponseEntity<StockItemResponse> restock(
      @PathVariable @Pattern(regexp = SKU_REGEX, message = SKU_MESSAGE) String sku,
      @Parameter(description = "ETag do produto, ex.: \"3\"")
          @RequestHeader(name = HttpHeaders.IF_MATCH, required = false)
          String ifMatch,
      @Valid @RequestBody RestockRequest request) {
    long expectedVersion = StockItemETag.requiredVersion(ifMatch);
    StockItem item = commands.restock(new Sku(sku), request.quantity(), expectedVersion);
    return withETag(ResponseEntity.ok(), item);
  }

  private static ResponseEntity<StockItemResponse> withETag(
      ResponseEntity.BodyBuilder builder, StockItem item) {
    return builder.eTag(StockItemETag.of(item.version())).body(StockItemResponse.of(item));
  }

  /**
   * Página da listagem de produtos.
   *
   * @param nextCursor valor para o parâmetro {@code cursor} da próxima página; nulo na última
   */
  record StockPageResponse(
      List<StockItemResponse> items, @Schema(types = {"string", "null"}) String nextCursor) {}

  /**
   * Situação do produto.
   *
   * @param available unidades livres para novos pedidos
   * @param reserved unidades reservadas para pedidos confirmados ainda não enviados
   * @param version versão atual; o ETag do produto é essa versão entre aspas
   */
  record StockItemResponse(
      String sku,
      String name,
      String currency,
      BigDecimal unitPrice,
      int available,
      int reserved,
      long version) {

    static StockItemResponse of(StockItem item) {
      return new StockItemResponse(
          item.sku().value(),
          item.name(),
          item.currency().getCurrencyCode(),
          item.unitPrice(),
          item.available(),
          item.reserved(),
          item.version());
    }
  }
}

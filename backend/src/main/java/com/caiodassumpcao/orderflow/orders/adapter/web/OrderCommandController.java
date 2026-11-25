package com.caiodassumpcao.orderflow.orders.adapter.web;

import com.caiodassumpcao.orderflow.orders.application.OrderCommandService;
import com.caiodassumpcao.orderflow.orders.application.PlaceOrderCommand;
import com.caiodassumpcao.orderflow.orders.application.PlaceOrderResult;
import com.caiodassumpcao.orderflow.orders.domain.CustomerId;
import com.caiodassumpcao.orderflow.orders.domain.Order;
import com.caiodassumpcao.orderflow.orders.domain.OrderId;
import com.caiodassumpcao.orderflow.shared.security.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Comandos de pedidos (modelo de escrita). Consultas ficam no modelo de leitura (Fase 3). O cliente
 * é sempre o {@code sub} do token, nunca um campo do corpo.
 */
@RestController
@RequestMapping(path = "/api/v1/orders", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Orders", description = "Comandos de escrita de pedidos")
class OrderCommandController {

  static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
  static final String REPLAYED_HEADER = "Idempotency-Replayed";

  private final OrderCommandService service;

  OrderCommandController(OrderCommandService service) {
    this.service = service;
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize(Roles.HAS_USER)
  @Operation(
      summary = "Cria um pedido",
      description =
          "Repetir a requisição com a mesma Idempotency-Key devolve o pedido original "
              + "(header Idempotency-Replayed: true); reutilizar a chave com outro conteúdo é 422.")
  @ApiResponse(responseCode = "201", description = "Pedido criado, ou o original numa repetição")
  ResponseEntity<OrderResponse> placeOrder(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "8 a 128 caracteres: letras, dígitos, '-' ou '_'")
          @RequestHeader(IDEMPOTENCY_KEY_HEADER)
          @Pattern(
              regexp = "^[A-Za-z0-9_-]{8,128}$",
              message = "must have 8 to 128 letters, digits, '-' or '_'")
          String idempotencyKey,
      @Valid @RequestBody PlaceOrderRequest request) {
    PlaceOrderResult result =
        service.placeOrder(
            new PlaceOrderCommand(
                customer(jwt),
                idempotencyKey,
                request.currency(),
                request.items().stream()
                    .map(item ->
                        new PlaceOrderCommand.Item(item.sku(), item.quantity(), item.unitPrice()))
                    .toList()));
    Order order = result.order();
    return ResponseEntity.created(URI.create("/api/v1/orders/" + order.id()))
        .header(REPLAYED_HEADER, Boolean.toString(result.replayed()))
        .body(OrderResponse.of(order));
  }

  @PostMapping("/{orderId}/cancel")
  @PreAuthorize(Roles.HAS_USER)
  @Operation(summary = "Cancela um pedido do próprio cliente (antes da confirmação)")
  ResponseEntity<OrderResponse> cancelOrder(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID orderId,
      @Valid @RequestBody(required = false) CancelOrderRequest request) {
    String reason = request == null ? null : request.reason();
    Order order = service.cancelOrder(new OrderId(orderId), customer(jwt), reason);
    return ResponseEntity.ok(OrderResponse.of(order));
  }

  @PostMapping("/{orderId}/ship")
  @PreAuthorize(Roles.HAS_ADMIN)
  @Operation(summary = "Marca um pedido confirmado como enviado (papel ADMIN)")
  ResponseEntity<OrderResponse> shipOrder(@PathVariable UUID orderId) {
    return ResponseEntity.ok(OrderResponse.of(service.shipOrder(new OrderId(orderId))));
  }

  private static CustomerId customer(Jwt jwt) {
    return new CustomerId(jwt.getSubject());
  }
}

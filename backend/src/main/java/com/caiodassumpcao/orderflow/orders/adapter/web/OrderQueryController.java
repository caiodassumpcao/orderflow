package com.caiodassumpcao.orderflow.orders.adapter.web;

import com.caiodassumpcao.orderflow.orders.application.query.OrderListQuery;
import com.caiodassumpcao.orderflow.orders.application.query.OrderQueryService;
import com.caiodassumpcao.orderflow.orders.domain.CustomerId;
import com.caiodassumpcao.orderflow.orders.domain.OrderId;
import com.caiodassumpcao.orderflow.orders.domain.OrderStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consultas de pedidos (modelo de leitura). Eventualmente consistente: logo após um comando, a
 * mudança pode levar alguns instantes para aparecer. O cliente só vê os próprios pedidos.
 */
@RestController
@RequestMapping(path = "/api/v1/orders", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Orders")
class OrderQueryController {

  private final OrderQueryService queries;

  OrderQueryController(OrderQueryService queries) {
    this.queries = queries;
  }

  @GetMapping
  @Operation(
      summary = "Lista os pedidos do cliente",
      description =
          "Do mais novo para o mais antigo, com paginação por cursor: envie o nextCursor da "
              + "resposta no parâmetro cursor para obter a página seguinte.")
  OrderPageResponse listOrders(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "Filtra por status") @RequestParam(required = false)
          OrderStatus status,
      @Parameter(description = "Itens por página (1 a 100)")
          @RequestParam(defaultValue = "" + OrderListQuery.DEFAULT_LIMIT)
          @Min(1)
          @Max(OrderListQuery.MAX_LIMIT)
          int limit,
      @Parameter(description = "nextCursor da página anterior") @RequestParam(required = false)
          String cursor) {
    OrderListQuery query =
        new OrderListQuery(
            customer(jwt),
            Optional.ofNullable(status),
            limit,
            Optional.ofNullable(cursor).map(PageCursorCodec::decode));
    return OrderPageResponse.of(queries.listOrders(query));
  }

  @GetMapping("/{orderId}")
  @Operation(summary = "Detalhe de um pedido do cliente")
  OrderResponse getOrder(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
    return OrderResponse.of(queries.getOrder(new OrderId(orderId), customer(jwt)));
  }

  private static CustomerId customer(Jwt jwt) {
    return new CustomerId(jwt.getSubject());
  }
}

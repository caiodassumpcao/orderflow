package com.caiodassumpcao.orderflow.orders.adapter.web;

import com.caiodassumpcao.orderflow.orders.application.query.AdminOrderListQuery;
import com.caiodassumpcao.orderflow.orders.application.query.OrderDetail;
import com.caiodassumpcao.orderflow.orders.application.query.OrderListQuery;
import com.caiodassumpcao.orderflow.orders.application.query.OrderPage;
import com.caiodassumpcao.orderflow.orders.application.query.OrderQueryService;
import com.caiodassumpcao.orderflow.orders.application.query.OrderSummary;
import com.caiodassumpcao.orderflow.orders.domain.CustomerId;
import com.caiodassumpcao.orderflow.orders.domain.OrderId;
import com.caiodassumpcao.orderflow.orders.domain.OrderStatus;
import com.caiodassumpcao.orderflow.shared.security.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Visão de administrador: todos os pedidos, com o cliente de cada um (papel ADMIN). */
@RestController
@RequestMapping(path = "/api/v1/admin/orders", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Admin", description = "Operação: todos os pedidos")
class AdminOrderController {

  private final OrderQueryService queries;

  AdminOrderController(OrderQueryService queries) {
    this.queries = queries;
  }

  @GetMapping
  @PreAuthorize(Roles.HAS_ADMIN)
  @Operation(
      operationId = "listAllOrders",
      summary = "Lista todos os pedidos, do mais novo para o mais antigo")
  AdminOrderPageResponse listOrders(
      @Parameter(description = "Filtra por status") @RequestParam(required = false)
          OrderStatus status,
      @Parameter(description = "Filtra por cliente (sub do token)")
          @RequestParam(required = false)
          @Pattern(regexp = "^[A-Za-z0-9._:@-]{1,64}$", message = "must be a valid customer id")
          String customerId,
      @RequestParam(defaultValue = "" + OrderListQuery.DEFAULT_LIMIT)
          @Min(1)
          @Max(OrderListQuery.MAX_LIMIT)
          int limit,
      @RequestParam(required = false) String cursor) {
    OrderPage page =
        queries.listAllOrders(
            new AdminOrderListQuery(
                Optional.ofNullable(customerId).map(CustomerId::new),
                Optional.ofNullable(status),
                limit,
                Optional.ofNullable(cursor).map(PageCursorCodec::decode)));
    return new AdminOrderPageResponse(
        page.items().stream().map(AdminOrderSummary::of).toList(),
        page.next().map(PageCursorCodec::encode).orElse(null));
  }

  @GetMapping("/{orderId}")
  @PreAuthorize(Roles.HAS_ADMIN)
  @Operation(operationId = "getAnyOrder", summary = "Detalhe de qualquer pedido")
  AdminOrderResponse getOrder(@PathVariable UUID orderId) {
    OrderDetail detail = queries.getAnyOrder(new OrderId(orderId));
    return new AdminOrderResponse(detail.summary().customerId().value(), OrderResponse.of(detail));
  }

  /** Página da listagem de administrador. */
  record AdminOrderPageResponse(
      List<AdminOrderSummary> items, @Schema(types = {"string", "null"}) String nextCursor) {}

  /** Linha da listagem de administrador. */
  record AdminOrderSummary(
      UUID id,
      String customerId,
      @Schema(implementation = OrderStatus.class) String status,
      String currency,
      BigDecimal total,
      int itemCount,
      Instant createdAt,
      Instant updatedAt) {

    static AdminOrderSummary of(OrderSummary summary) {
      return new AdminOrderSummary(
          summary.id().value(),
          summary.customerId().value(),
          summary.status().name(),
          summary.currency().getCurrencyCode(),
          summary.total(),
          summary.itemCount(),
          summary.createdAt(),
          summary.updatedAt());
    }
  }

  /** Detalhe para administrador: o pedido e o cliente dono dele. */
  record AdminOrderResponse(String customerId, OrderResponse order) {}
}

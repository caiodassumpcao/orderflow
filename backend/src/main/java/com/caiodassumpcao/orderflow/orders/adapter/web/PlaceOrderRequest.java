package com.caiodassumpcao.orderflow.orders.adapter.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * Corpo de {@code POST /api/v1/orders}. As regras repetem as do domínio para responder 400 cedo.
 */
record PlaceOrderRequest(
    @Schema(example = "BRL", description = "Código ISO 4217")
        @NotBlank
        @Pattern(regexp = "^[A-Z]{3}$", message = "must be a 3-letter ISO 4217 code")
        String currency,
    @NotNull @Size(min = 1, max = 50) List<@NotNull @Valid Item> items) {

  /** Item do pedido. */
  record Item(
      @Schema(example = "SKU-001")
          @NotBlank
          @Pattern(
              regexp = "^[A-Z0-9][A-Z0-9-]{2,31}$",
              message = "must have 3 to 32 uppercase letters, digits or hyphens")
          String sku,
      @Schema(example = "2") @NotNull @Min(1) @Max(1000) Integer quantity,
      @Schema(example = "49.90")
          @NotNull
          @DecimalMin("0.01")
          @DecimalMax("1000000.00")
          @Digits(integer = 7, fraction = 2)
          BigDecimal unitPrice) {}
}

package com.caiodassumpcao.orderflow.inventory.adapter.web;

import io.swagger.v3.oas.annotations.media.Schema;
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

/**
 * Corpo de {@code POST /api/v1/inventory}. Os limites de preço são os mesmos dos itens de pedido.
 */
record RegisterProductRequest(
    @Schema(example = "SKU-006")
        @NotBlank
        @Pattern(regexp = InventoryController.SKU_REGEX, message = InventoryController.SKU_MESSAGE)
        String sku,
    @Schema(example = "Garrafa térmica")
        @NotBlank
        @Size(max = 120)
        @Pattern(regexp = "^\\P{Cc}*$", message = "must not contain control characters")
        String name,
    @Schema(example = "BRL", description = "Código ISO 4217")
        @NotBlank
        @Pattern(regexp = "^[A-Z]{3}$", message = "must be a 3-letter ISO 4217 code")
        String currency,
    @Schema(example = "89.90")
        @NotNull
        @DecimalMin("0.01")
        @DecimalMax("1000000.00")
        @Digits(integer = 7, fraction = 2)
        BigDecimal unitPrice,
    @Schema(example = "100") @NotNull @Min(0) @Max(1_000_000) Integer initialQuantity) {}

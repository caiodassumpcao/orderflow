package com.caiodassumpcao.orderflow.inventory.adapter.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Corpo de {@code POST /api/v1/inventory/{sku}/restock}. */
record RestockRequest(
    @Schema(example = "50", description = "Unidades a repor (1 a 100000)")
        @NotNull
        @Min(1)
        @Max(100_000)
        Integer quantity) {}

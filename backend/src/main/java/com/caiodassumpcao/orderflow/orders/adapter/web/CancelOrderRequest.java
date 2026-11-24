package com.caiodassumpcao.orderflow.orders.adapter.web;

import jakarta.validation.constraints.Size;

/** Corpo opcional de {@code POST /api/v1/orders/{id}/cancel}. */
record CancelOrderRequest(@Size(max = 200) String reason) {}

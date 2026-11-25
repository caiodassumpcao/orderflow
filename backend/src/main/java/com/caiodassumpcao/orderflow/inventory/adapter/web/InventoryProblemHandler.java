package com.caiodassumpcao.orderflow.inventory.adapter.web;

import com.caiodassumpcao.orderflow.inventory.application.StockItemNotFoundException;
import com.caiodassumpcao.orderflow.shared.error.Problems;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Traduz as exceções do inventário em Problem Details (catálogo em docs/api). */
@RestControllerAdvice(basePackageClasses = InventoryController.class)
class InventoryProblemHandler {

  @ExceptionHandler(StockItemNotFoundException.class)
  ResponseEntity<ProblemDetail> notFound(
      StockItemNotFoundException ex, HttpServletRequest request) {
    return Problems.response(
        HttpStatus.NOT_FOUND,
        "stock-item-not-found",
        "Stock item not found",
        ex.getMessage(),
        request);
  }
}

package com.caiodassumpcao.orderflow.inventory.adapter.web;

import com.caiodassumpcao.orderflow.inventory.application.StockItemAlreadyExistsException;
import com.caiodassumpcao.orderflow.inventory.application.StockItemModifiedException;
import com.caiodassumpcao.orderflow.inventory.application.StockItemNotFoundException;
import com.caiodassumpcao.orderflow.inventory.domain.StockLimitExceededException;
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

  @ExceptionHandler(StockItemAlreadyExistsException.class)
  ResponseEntity<ProblemDetail> alreadyExists(
      StockItemAlreadyExistsException ex, HttpServletRequest request) {
    return Problems.response(
        HttpStatus.CONFLICT,
        "stock-item-already-exists",
        "Stock item already exists",
        ex.getMessage(),
        request);
  }

  @ExceptionHandler(StockItemModifiedException.class)
  ResponseEntity<ProblemDetail> modified(
      StockItemModifiedException ex, HttpServletRequest request) {
    ResponseEntity<ProblemDetail> response =
        Problems.response(
            HttpStatus.PRECONDITION_FAILED,
            "stock-item-modified",
            "Stock item modified",
            ex.getMessage(),
            request);
    return ResponseEntity.status(response.getStatusCode())
        .headers(response.getHeaders())
        .eTag(StockItemETag.of(ex.currentVersion()))
        .body(response.getBody());
  }

  @ExceptionHandler(PreconditionRequiredException.class)
  ResponseEntity<ProblemDetail> preconditionRequired(
      PreconditionRequiredException ex, HttpServletRequest request) {
    return Problems.response(
        HttpStatus.PRECONDITION_REQUIRED,
        "precondition-required",
        "Precondition required",
        ex.getMessage(),
        request);
  }

  @ExceptionHandler(StockLimitExceededException.class)
  ResponseEntity<ProblemDetail> limitExceeded(
      StockLimitExceededException ex, HttpServletRequest request) {
    return Problems.response(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "stock-limit-exceeded",
        "Stock limit exceeded",
        ex.getMessage(),
        request);
  }
}

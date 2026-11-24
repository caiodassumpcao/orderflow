package com.caiodassumpcao.orderflow.orders.adapter.web;

import com.caiodassumpcao.orderflow.orders.application.IdempotencyKeyReusedException;
import com.caiodassumpcao.orderflow.orders.application.OrderNotFoundException;
import com.caiodassumpcao.orderflow.orders.application.port.ConcurrentOrderUpdateException;
import com.caiodassumpcao.orderflow.orders.application.port.IdempotencyKeyTakenException;
import com.caiodassumpcao.orderflow.orders.domain.IllegalOrderStateException;
import com.caiodassumpcao.orderflow.orders.domain.InvalidOrderException;
import com.caiodassumpcao.orderflow.shared.error.Problems;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Traduz as exceções do contexto de pedidos em Problem Details (catálogo em docs/api). */
@RestControllerAdvice(basePackageClasses = OrderCommandController.class)
class OrderProblemHandler {

  @ExceptionHandler(InvalidOrderException.class)
  ResponseEntity<ProblemDetail> invalidOrder(InvalidOrderException ex, HttpServletRequest request) {
    // Mensagens do domínio descrevem a regra violada e não expõem detalhes internos.
    return Problems.response(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "invalid-order",
        "Order violates a business rule",
        ex.getMessage(),
        request);
  }

  @ExceptionHandler(IllegalOrderStateException.class)
  ResponseEntity<ProblemDetail> illegalState(
      IllegalOrderStateException ex, HttpServletRequest request) {
    ResponseEntity<ProblemDetail> response =
        Problems.response(
            HttpStatus.CONFLICT,
            "order-state-conflict",
            "Operation not allowed in the current order status",
            ex.getMessage(),
            request);
    ProblemDetail body = response.getBody();
    if (body != null) {
      body.setProperty("currentStatus", ex.currentStatus().name());
    }
    return response;
  }

  @ExceptionHandler(OrderNotFoundException.class)
  ResponseEntity<ProblemDetail> notFound(OrderNotFoundException ex, HttpServletRequest request) {
    return Problems.response(
        HttpStatus.NOT_FOUND, "order-not-found", "Order not found", ex.getMessage(), request);
  }

  @ExceptionHandler(IdempotencyKeyReusedException.class)
  ResponseEntity<ProblemDetail> keyReused(
      IdempotencyKeyReusedException ex, HttpServletRequest request) {
    return Problems.response(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "idempotency-key-reused",
        "Idempotency key reused",
        "This Idempotency-Key was already used with a different request body.",
        request);
  }

  @ExceptionHandler({ConcurrentOrderUpdateException.class, IdempotencyKeyTakenException.class})
  ResponseEntity<ProblemDetail> concurrentModification(
      RuntimeException ex, HttpServletRequest request) {
    return Problems.response(
        HttpStatus.CONFLICT,
        "concurrent-modification",
        "Concurrent modification",
        "The order was changed by another request. Retry the operation.",
        request);
  }
}

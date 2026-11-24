package com.caiodassumpcao.orderflow.shared.error;

import com.caiodassumpcao.orderflow.shared.observability.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Tratamento global de erros no formato RFC 9457 (Problem Details). Erros do Spring MVC são
 * tratados pela superclasse; falhas de segurança chegam aqui pelos handlers de {@code
 * shared.security}. Mensagens internas de exceções nunca são expostas ao cliente.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  static final String CORRELATION_ID_PROPERTY = "correlationId";

  @ExceptionHandler(AuthenticationException.class)
  ResponseEntity<ProblemDetail> handleAuthentication(
      AuthenticationException ex, HttpServletRequest request) {
    return problem(
        HttpStatus.UNAUTHORIZED, "Authentication is required to access this resource.", request);
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ProblemDetail> handleAccessDenied(
      AccessDeniedException ex, HttpServletRequest request) {
    return problem(HttpStatus.FORBIDDEN, "You do not have permission for this resource.", request);
  }

  /** Garante o correlationId também nos erros tratados pelo Spring MVC (400, 404, 405...). */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex,
      Object body,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    ResponseEntity<Object> response =
        super.handleExceptionInternal(ex, body, headers, statusCode, request);
    if (response != null && response.getBody() instanceof ProblemDetail problem) {
      addCorrelationId(problem);
    }
    return response;
  }

  private static ResponseEntity<ProblemDetail> problem(
      HttpStatus status, String detail, HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setInstance(URI.create(request.getRequestURI()));
    addCorrelationId(problem);
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  private static void addCorrelationId(ProblemDetail problem) {
    String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
    if (correlationId != null) {
      problem.setProperty(CORRELATION_ID_PROPERTY, correlationId);
    }
  }
}

package com.caiodassumpcao.orderflow.shared.error;

import com.caiodassumpcao.orderflow.shared.security.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Tratamento global de erros no formato RFC 9457 (Problem Details). Erros do Spring MVC são
 * tratados pela superclasse; falhas de segurança chegam aqui pelos handlers de {@code
 * shared.security}. Mensagens internas de exceções nunca são expostas ao cliente.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  static final String VALIDATION_TYPE = "validation-error";
  static final String ERRORS_PROPERTY = "errors";

  @ExceptionHandler(AuthenticationException.class)
  ResponseEntity<ProblemDetail> handleAuthentication(
      AuthenticationException ex, HttpServletRequest request) {
    return Problems.response(
        HttpStatus.UNAUTHORIZED,
        null,
        null,
        "Authentication is required to access this resource.",
        request);
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ProblemDetail> handleAccessDenied(
      AccessDeniedException ex, HttpServletRequest request) {
    return Problems.response(
        HttpStatus.FORBIDDEN, null, null, "You do not have permission for this resource.", request);
  }

  @ExceptionHandler(RateLimitExceededException.class)
  ResponseEntity<ProblemDetail> handleRateLimit(
      RateLimitExceededException ex, HttpServletRequest request) {
    ResponseEntity<ProblemDetail> problem =
        Problems.response(
            HttpStatus.TOO_MANY_REQUESTS,
            "rate-limit-exceeded",
            "Too many requests",
            "Too many write requests. Retry after " + ex.retryAfterSeconds() + " seconds.",
            request);
    return ResponseEntity.status(problem.getStatusCode())
        .headers(problem.getHeaders())
        .header(HttpHeaders.RETRY_AFTER, Long.toString(ex.retryAfterSeconds()))
        .body(problem.getBody());
  }

  /** Corpo inválido: lista cada campo rejeitado em {@code errors}. */
  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<Map<String, String>> errors =
        ex.getBindingResult().getAllErrors().stream()
            .map(error ->
                fieldError(
                    error instanceof FieldError field ? field.getField() : error.getObjectName(),
                    error))
            .sorted(Comparator.comparing(error -> error.get("field")))
            .toList();
    return validationProblem(ex, headers, errors, request);
  }

  /**
   * Validação do método inteiro (Spring 6.1+), usada quando algum parâmetro tem restrição: cobre
   * headers, path e também os campos do corpo. Erros de campo usam o caminho do campo; os demais, o
   * nome do parâmetro.
   */
  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<Map<String, String>> errors =
        ex.getParameterValidationResults().stream()
            .flatMap(result ->
                result.getResolvableErrors().stream()
                    .map(error ->
                        fieldError(
                            error instanceof FieldError field
                                ? field.getField()
                                : result.getMethodParameter().getParameterName(),
                            error)))
            .sorted(Comparator.comparing(error -> error.get("field")))
            .toList();
    return validationProblem(ex, headers, errors, request);
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
      Problems.addCorrelationId(problem);
    }
    return response;
  }

  private ResponseEntity<Object> validationProblem(
      Exception ex, HttpHeaders headers, List<Map<String, String>> errors, WebRequest request) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "One or more fields are invalid.");
    problem.setType(Problems.typeUri(VALIDATION_TYPE));
    problem.setTitle("Invalid request");
    problem.setProperty(ERRORS_PROPERTY, errors);
    return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
  }

  private static Map<String, String> fieldError(String field, MessageSourceResolvable error) {
    String message = error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage();
    return Map.of("field", field == null ? "request" : field, "message", message);
  }
}

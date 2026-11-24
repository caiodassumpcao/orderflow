package com.caiodassumpcao.orderflow.shared.error;

import com.caiodassumpcao.orderflow.shared.observability.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/**
 * Fábrica de respostas RFC 9457. O {@code type} aponta para a seção do catálogo de erros em {@code
 * docs/api/problems.md}; o {@code correlationId} da requisição é sempre incluído.
 */
public final class Problems {

  public static final String TYPE_BASE =
      "https://github.com/caiodassumpcao/orderflow/blob/main/docs/api/problems.md#";
  public static final String CORRELATION_ID_PROPERTY = "correlationId";

  private Problems() {}

  /**
   * Monta a resposta de erro.
   *
   * @param status status HTTP
   * @param type identificador do tipo no catálogo (âncora), ou {@code null} para {@code
   *     about:blank}
   * @param title resumo curto e estável do tipo de erro
   * @param detail explicação desta ocorrência, segura para o cliente
   * @param request requisição de origem (vira o {@code instance})
   */
  public static ResponseEntity<ProblemDetail> response(
      HttpStatus status, String type, String title, String detail, HttpServletRequest request) {
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem(status, type, title, detail, request));
  }

  public static ProblemDetail problem(
      HttpStatus status, String type, String title, String detail, HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    if (type != null) {
      problem.setType(typeUri(type));
    }
    if (title != null) {
      problem.setTitle(title);
    }
    problem.setInstance(URI.create(request.getRequestURI()));
    addCorrelationId(problem);
    return problem;
  }

  public static URI typeUri(String type) {
    return URI.create(TYPE_BASE + type);
  }

  public static void addCorrelationId(ProblemDetail problem) {
    String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
    if (correlationId != null) {
      problem.setProperty(CORRELATION_ID_PROPERTY, correlationId);
    }
  }
}

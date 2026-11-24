package com.caiodassumpcao.orderflow.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/** Responde 403 com header {@code WWW-Authenticate} (RFC 6750) e corpo RFC 9457. */
@Component
public class ProblemDetailsAccessDeniedHandler implements AccessDeniedHandler {

  private final BearerTokenAccessDeniedHandler bearerHandler = new BearerTokenAccessDeniedHandler();
  private final HandlerExceptionResolver resolver;

  public ProblemDetailsAccessDeniedHandler(
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
    this.resolver = resolver;
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException) {
    bearerHandler.handle(request, response, accessDeniedException);
    resolver.resolveException(request, response, null, accessDeniedException);
  }
}

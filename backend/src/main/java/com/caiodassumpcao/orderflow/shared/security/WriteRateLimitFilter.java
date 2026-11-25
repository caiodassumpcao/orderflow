package com.caiodassumpcao.orderflow.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Limita os comandos (POST, PUT, PATCH, DELETE em {@code /api/**}) por usuário autenticado. Roda
 * depois da autenticação; consultas não são limitadas. Excedido o limite, responde 429 em Problem
 * Details com {@code Retry-After}.
 */
public class WriteRateLimitFilter extends OncePerRequestFilter {

  private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

  private final TokenBucketRateLimiter limiter;
  private final HandlerExceptionResolver resolver;

  public WriteRateLimitFilter(TokenBucketRateLimiter limiter, HandlerExceptionResolver resolver) {
    this.limiter = limiter;
    this.resolver = resolver;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !WRITE_METHODS.contains(request.getMethod())
        || !request.getRequestURI().startsWith(request.getContextPath() + "/api/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      // Sem usuário a autorização responde 401 adiante; não há chave para limitar.
      chain.doFilter(request, response);
      return;
    }
    TokenBucketRateLimiter.Decision decision = limiter.tryConsume(authentication.getName());
    if (decision.allowed()) {
      chain.doFilter(request, response);
      return;
    }
    response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
    resolver.resolveException(
        request, response, null, new RateLimitExceededException(decision.retryAfterSeconds()));
  }
}

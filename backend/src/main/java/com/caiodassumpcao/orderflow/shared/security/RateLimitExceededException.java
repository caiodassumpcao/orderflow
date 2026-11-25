package com.caiodassumpcao.orderflow.shared.security;

/** O usuário excedeu o limite de comandos; deve esperar {@code retryAfterSeconds}. */
public class RateLimitExceededException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final long retryAfterSeconds;

  public RateLimitExceededException(long retryAfterSeconds) {
    super("Too many write requests; retry after " + retryAfterSeconds + "s");
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public long retryAfterSeconds() {
    return retryAfterSeconds;
  }
}

package com.caiodassumpcao.orderflow.orders.application.port;

/** Outra requisição concorrente registrou a mesma chave de idempotência primeiro. */
public class IdempotencyKeyTakenException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public IdempotencyKeyTakenException(Throwable cause) {
    super("Idempotency key was registered by a concurrent request", cause);
  }
}

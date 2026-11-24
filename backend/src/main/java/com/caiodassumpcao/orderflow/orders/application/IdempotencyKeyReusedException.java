package com.caiodassumpcao.orderflow.orders.application;

/** A chave de idempotência já foi usada pelo cliente com um conteúdo diferente. */
public class IdempotencyKeyReusedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public IdempotencyKeyReusedException() {
    super("Idempotency key was already used with a different request");
  }
}

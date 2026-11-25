package com.caiodassumpcao.orderflow.shared.messaging;

/**
 * Mensagem que nunca poderá ser processada (JSON inválido, envelope incompleto, versão
 * desconhecida). Não é retentada: vai direto para a DLQ do consumer.
 */
public class PoisonMessageException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public PoisonMessageException(String message) {
    super(message);
  }

  public PoisonMessageException(String message, Throwable cause) {
    super(message, cause);
  }
}

package com.caiodassumpcao.orderflow.orders.domain;

/** Um pedido (ou um de seus valores) violaria uma invariante do domínio. */
public class InvalidOrderException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public InvalidOrderException(String message) {
    super(message);
  }
}

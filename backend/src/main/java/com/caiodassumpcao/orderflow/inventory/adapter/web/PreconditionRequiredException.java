package com.caiodassumpcao.orderflow.inventory.adapter.web;

/** A operação exige {@code If-Match} e o cliente não o enviou (RFC 6585, 428). */
class PreconditionRequiredException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  PreconditionRequiredException() {
    super("This operation requires an If-Match header with the current ETag of the product.");
  }
}

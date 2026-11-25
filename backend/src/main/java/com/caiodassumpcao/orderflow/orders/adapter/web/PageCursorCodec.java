package com.caiodassumpcao.orderflow.orders.adapter.web;

import com.caiodassumpcao.orderflow.orders.application.query.PageCursor;
import com.caiodassumpcao.orderflow.orders.domain.OrderId;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

/**
 * Cursor opaco da listagem: base64url de {@code createdAt|orderId}. O cliente só repassa o valor
 * recebido em {@code nextCursor}; o formato pode mudar sem quebrar a API.
 */
final class PageCursorCodec {

  static final int MAX_LENGTH = 200;
  private static final char SEPARATOR = '|';

  private PageCursorCodec() {}

  static String encode(PageCursor cursor) {
    String raw = cursor.createdAt().toString() + SEPARATOR + cursor.orderId();
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Lê um cursor recebido do cliente.
   *
   * @throws InvalidCursorException se o valor não foi produzido por {@link #encode}
   */
  static PageCursor decode(String value) {
    if (value == null || value.isBlank() || value.length() > MAX_LENGTH) {
      throw new InvalidCursorException();
    }
    try {
      String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
      int separator = raw.indexOf(SEPARATOR);
      if (separator < 0) {
        throw new InvalidCursorException();
      }
      return new PageCursor(
          Instant.parse(raw.substring(0, separator)),
          new OrderId(UUID.fromString(raw.substring(separator + 1))));
    } catch (IllegalArgumentException | DateTimeParseException e) {
      throw new InvalidCursorException();
    }
  }

  /** Cursor que não foi gerado pela API (adulterado, truncado ou de outra versão). */
  static final class InvalidCursorException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    InvalidCursorException() {
      super("Invalid page cursor");
    }
  }
}

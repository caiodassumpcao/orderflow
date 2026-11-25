package com.caiodassumpcao.orderflow.inventory.adapter.web;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ETag forte de um produto: a versão entre aspas (RFC 9110, 8.8.3). Só um ETag forte vindo de uma
 * resposta anterior satisfaz o {@code If-Match}; {@code *} e ETags fracos não são aceitos, porque
 * não provam que o cliente viu o estado atual.
 */
final class StockItemETag {

  private static final Pattern STRONG = Pattern.compile("^\"(\\d{1,18})\"$");

  private StockItemETag() {}

  static String of(long version) {
    return "\"" + version + "\"";
  }

  /**
   * Versão exigida pelo {@code If-Match}.
   *
   * @throws PreconditionRequiredException se o header não foi enviado
   * @return a versão do ETag, ou {@code -1} (nunca casa) se o valor não é um ETag forte nosso
   */
  static long requiredVersion(String ifMatch) {
    if (ifMatch == null || ifMatch.isBlank()) {
      throw new PreconditionRequiredException();
    }
    Matcher matcher = STRONG.matcher(ifMatch.strip());
    return matcher.matches() ? Long.parseLong(matcher.group(1)) : -1;
  }
}

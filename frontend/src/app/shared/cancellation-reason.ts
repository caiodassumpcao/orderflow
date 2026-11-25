const STOCK_REJECTION = /^Stock rejected: ([A-Z_]+)(?: \((.+)\))?$/;

const REJECTION_TEXT: Readonly<Record<string, string>> = {
  OUT_OF_STOCK: 'Estoque insuficiente',
  UNKNOWN_SKU: 'Produto inexistente no catálogo',
  PRICE_MISMATCH: 'Preço ou moeda diferentes do catálogo',
};

/**
 * Motivo do cancelamento para exibir. Recusas do inventário chegam no formato
 * `Stock rejected: OUT_OF_STOCK (SKU-1, SKU-2)` e são traduzidas; motivos escritos pelo cliente
 * aparecem como foram digitados.
 */
export function describeCancellation(reason: string | null): string | null {
  if (!reason) {
    return null;
  }
  const match = STOCK_REJECTION.exec(reason);
  if (!match) {
    return reason;
  }
  const text = REJECTION_TEXT[match[1]] ?? 'Recusado pelo estoque';
  return match[2] ? `${text} (${match[2]})` : text;
}

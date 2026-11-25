package com.caiodassumpcao.orderflow.inventory.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Produto em estoque: nome e preço de catálogo, quantidade livre para reservar ({@code available})
 * e quantidade reservada para pedidos confirmados ainda não enviados ({@code reserved}).
 */
public final class StockItem {

  public static final int MAX_NAME_LENGTH = 120;

  /** Teto de unidades livres por produto; protege contra estouro de {@code int} na reposição. */
  public static final int MAX_AVAILABLE = 1_000_000_000;

  private final Sku sku;
  private final String name;
  private final Currency currency;
  private final BigDecimal unitPrice;
  private int available;
  private int reserved;
  private final long version;

  public StockItem(
      Sku sku,
      String name,
      Currency currency,
      BigDecimal unitPrice,
      int available,
      int reserved,
      long version) {
    this.sku = Objects.requireNonNull(sku, "sku");
    this.name = normalizeName(name);
    this.currency = Objects.requireNonNull(currency, "currency");
    if (unitPrice == null || unitPrice.signum() <= 0) {
      throw new IllegalArgumentException("Unit price must be positive");
    }
    this.unitPrice = unitPrice.setScale(2, RoundingMode.UNNECESSARY);
    if (available < 0 || reserved < 0) {
      throw new IllegalArgumentException("Stock quantities must not be negative");
    }
    if (available > MAX_AVAILABLE) {
      throw new IllegalArgumentException("Available units must be at most " + MAX_AVAILABLE);
    }
    this.available = available;
    this.reserved = reserved;
    this.version = version;
  }

  /** Produto novo no catálogo, com as unidades iniciais livres e nenhuma reservada. */
  public static StockItem register(
      Sku sku, String name, Currency currency, BigDecimal unitPrice, int initialUnits) {
    return new StockItem(sku, name, currency, unitPrice, initialUnits, 0, 0);
  }

  /** Repõe unidades livres (entrada de mercadoria). */
  public void restock(int quantity) {
    if (quantity <= 0) {
      throw new IllegalArgumentException("Restock quantity must be positive");
    }
    if (quantity > MAX_AVAILABLE - available) {
      throw new StockLimitExceededException(sku, MAX_AVAILABLE);
    }
    available += quantity;
  }

  /** O preço e a moeda pedidos são os do catálogo? */
  public boolean isSoldAt(BigDecimal price, Currency requestedCurrency) {
    return currency.equals(requestedCurrency) && unitPrice.compareTo(price) == 0;
  }

  public boolean canReserve(int quantity) {
    return quantity > 0 && available >= quantity;
  }

  /** Move unidades de livres para reservadas. */
  public void reserve(int quantity) {
    if (!canReserve(quantity)) {
      throw new IllegalStateException(
          "Cannot reserve " + quantity + " of " + sku + " (available " + available + ")");
    }
    available -= quantity;
    reserved += quantity;
  }

  /** Devolve unidades reservadas ao estoque livre (pedido cancelado). */
  public void release(int quantity) {
    requireReserved(quantity);
    reserved -= quantity;
    available += quantity;
  }

  /** Dá baixa nas unidades reservadas (pedido enviado). */
  public void fulfill(int quantity) {
    requireReserved(quantity);
    reserved -= quantity;
  }

  private void requireReserved(int quantity) {
    if (quantity <= 0 || reserved < quantity) {
      throw new IllegalStateException(
          "Cannot take " + quantity + " reserved units of " + sku + " (reserved " + reserved + ")");
    }
  }

  private static String normalizeName(String name) {
    String trimmed = name == null ? "" : name.strip();
    if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
      throw new IllegalArgumentException(
          "Product name must have 1 to " + MAX_NAME_LENGTH + " characters");
    }
    if (trimmed.chars().anyMatch(Character::isISOControl)) {
      throw new IllegalArgumentException("Product name must not contain control characters");
    }
    return trimmed;
  }

  public Sku sku() {
    return sku;
  }

  public String name() {
    return name;
  }

  public Currency currency() {
    return currency;
  }

  public BigDecimal unitPrice() {
    return unitPrice;
  }

  public int available() {
    return available;
  }

  public int reserved() {
    return reserved;
  }

  public long version() {
    return version;
  }
}

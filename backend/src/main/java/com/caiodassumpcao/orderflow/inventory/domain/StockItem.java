package com.caiodassumpcao.orderflow.inventory.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Produto em estoque: preço de catálogo, quantidade livre para reservar ({@code available}) e
 * quantidade reservada para pedidos confirmados ainda não enviados ({@code reserved}).
 */
public final class StockItem {

  private final Sku sku;
  private final Currency currency;
  private final BigDecimal unitPrice;
  private int available;
  private int reserved;
  private final long version;

  public StockItem(
      Sku sku, Currency currency, BigDecimal unitPrice, int available, int reserved, long version) {
    this.sku = Objects.requireNonNull(sku, "sku");
    this.currency = Objects.requireNonNull(currency, "currency");
    if (unitPrice == null || unitPrice.signum() <= 0) {
      throw new IllegalArgumentException("Unit price must be positive");
    }
    this.unitPrice = unitPrice.setScale(2, RoundingMode.UNNECESSARY);
    if (available < 0 || reserved < 0) {
      throw new IllegalArgumentException("Stock quantities must not be negative");
    }
    this.available = available;
    this.reserved = reserved;
    this.version = version;
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

  public Sku sku() {
    return sku;
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

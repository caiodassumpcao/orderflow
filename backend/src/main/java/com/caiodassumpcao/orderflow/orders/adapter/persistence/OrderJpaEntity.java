package com.caiodassumpcao.orderflow.orders.adapter.persistence;

import com.caiodassumpcao.orderflow.orders.domain.OrderStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Mapeamento JPA do agregado {@code Order} no schema {@code command}. */
@Entity
@Table(schema = "command", name = "orders")
class OrderJpaEntity {

  @Id private UUID id;

  @Column(name = "customer_id", nullable = false, length = 64)
  private String customerId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private OrderStatus status;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
  private BigDecimal totalAmount;

  @Column(name = "cancellation_reason", length = 200)
  private String cancellationReason;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      schema = "command",
      name = "order_lines",
      joinColumns = @JoinColumn(name = "order_id"))
  @OrderColumn(name = "line_number")
  private List<OrderLineEmbeddable> lines = new ArrayList<>();

  protected OrderJpaEntity() {}

  OrderJpaEntity(
      UUID id,
      String customerId,
      String currency,
      BigDecimal totalAmount,
      Instant createdAt,
      List<OrderLineEmbeddable> lines) {
    this.id = id;
    this.customerId = customerId;
    this.currency = currency;
    this.totalAmount = totalAmount;
    this.createdAt = createdAt;
    this.lines = new ArrayList<>(lines);
  }

  /** Atualiza o estado mutável; itens, cliente e total não mudam depois da criação. */
  void updateState(OrderStatus status, String cancellationReason, Instant updatedAt) {
    this.status = status;
    this.cancellationReason = cancellationReason;
    this.updatedAt = updatedAt;
  }

  UUID id() {
    return id;
  }

  String customerId() {
    return customerId;
  }

  OrderStatus status() {
    return status;
  }

  String currency() {
    return currency;
  }

  String cancellationReason() {
    return cancellationReason;
  }

  Instant createdAt() {
    return createdAt;
  }

  Instant updatedAt() {
    return updatedAt;
  }

  Long version() {
    return version;
  }

  List<OrderLineEmbeddable> lines() {
    return lines;
  }
}

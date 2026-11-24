package com.caiodassumpcao.orderflow.orders.application;

import com.caiodassumpcao.orderflow.orders.application.port.IdempotencyKeyStore;
import com.caiodassumpcao.orderflow.orders.application.port.IdempotencyKeyStore.StoredKey;
import com.caiodassumpcao.orderflow.orders.application.port.IdempotencyKeyTakenException;
import com.caiodassumpcao.orderflow.orders.application.port.OrderEventOutbox;
import com.caiodassumpcao.orderflow.orders.application.port.OrderRepository;
import com.caiodassumpcao.orderflow.orders.application.port.UnitOfWork;
import com.caiodassumpcao.orderflow.orders.domain.CustomerId;
import com.caiodassumpcao.orderflow.orders.domain.InvalidOrderException;
import com.caiodassumpcao.orderflow.orders.domain.Money;
import com.caiodassumpcao.orderflow.orders.domain.Order;
import com.caiodassumpcao.orderflow.orders.domain.OrderEvent;
import com.caiodassumpcao.orderflow.orders.domain.OrderId;
import com.caiodassumpcao.orderflow.orders.domain.OrderLine;
import com.caiodassumpcao.orderflow.orders.domain.Sku;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Casos de uso de escrita de pedidos. Cada comando roda em uma única transação que grava o agregado
 * e seus eventos na outbox (sem dual-write).
 */
public class OrderCommandService {

  private final OrderRepository orders;
  private final OrderEventOutbox outbox;
  private final IdempotencyKeyStore idempotencyKeys;
  private final UnitOfWork unitOfWork;
  private final Clock clock;
  private final Supplier<OrderId> newOrderId;

  public OrderCommandService(
      OrderRepository orders,
      OrderEventOutbox outbox,
      IdempotencyKeyStore idempotencyKeys,
      UnitOfWork unitOfWork,
      Clock clock,
      Supplier<OrderId> newOrderId) {
    this.orders = Objects.requireNonNull(orders);
    this.outbox = Objects.requireNonNull(outbox);
    this.idempotencyKeys = Objects.requireNonNull(idempotencyKeys);
    this.unitOfWork = Objects.requireNonNull(unitOfWork);
    this.clock = Objects.requireNonNull(clock);
    this.newOrderId = Objects.requireNonNull(newOrderId);
  }

  /**
   * Cria um pedido. Repetir a mesma requisição com a mesma {@code Idempotency-Key} devolve o pedido
   * original sem criar outro; reutilizar a chave com conteúdo diferente é rejeitado.
   */
  public PlaceOrderResult placeOrder(PlaceOrderCommand command) {
    Currency currency = toCurrency(command.currency());
    List<OrderLine> lines = toLines(command, currency);
    String requestHash = fingerprint(currency, lines);
    try {
      return unitOfWork.inTransaction(() -> placeOrReplay(command, lines, requestHash));
    } catch (IdempotencyKeyTakenException concurrentRequest) {
      // Uma requisição concorrente com a mesma chave venceu: devolve o resultado dela.
      return unitOfWork.inTransaction(() ->
          replay(
              idempotencyKeys
                  .find(command.customerId(), command.idempotencyKey())
                  .orElseThrow(() -> concurrentRequest),
              requestHash));
    }
  }

  /** Cancela um pedido do próprio cliente. */
  public Order cancelOrder(OrderId orderId, CustomerId requester, String reason) {
    return unitOfWork.inTransaction(() -> {
      Order order =
          orders
              .findById(orderId)
              .filter(found -> found.isOwnedBy(requester))
              .orElseThrow(() -> new OrderNotFoundException(orderId));
      order.cancel(reason, clock.instant());
      return saveWithEvents(order);
    });
  }

  /** Marca um pedido confirmado como enviado. */
  public Order shipOrder(OrderId orderId) {
    return unitOfWork.inTransaction(() -> {
      Order order = orders.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
      order.ship(clock.instant());
      return saveWithEvents(order);
    });
  }

  private PlaceOrderResult placeOrReplay(
      PlaceOrderCommand command, List<OrderLine> lines, String requestHash) {
    var existing = idempotencyKeys.find(command.customerId(), command.idempotencyKey());
    if (existing.isPresent()) {
      return replay(existing.get(), requestHash);
    }
    Order order = Order.place(newOrderId.get(), command.customerId(), lines, clock.instant());
    List<OrderEvent> events = order.pullEvents();
    orders.save(order);
    idempotencyKeys.save(command.customerId(), command.idempotencyKey(), requestHash, order.id());
    outbox.append(events);
    return new PlaceOrderResult(order, false);
  }

  private PlaceOrderResult replay(StoredKey stored, String requestHash) {
    if (!stored.requestHash().equals(requestHash)) {
      throw new IdempotencyKeyReusedException();
    }
    Order original =
        orders
            .findById(stored.orderId())
            .orElseThrow(() -> new IllegalStateException("Idempotency key points to no order"));
    return new PlaceOrderResult(original, true);
  }

  private Order saveWithEvents(Order order) {
    var events = order.pullEvents();
    orders.save(order);
    outbox.append(events);
    return order;
  }

  private static List<OrderLine> toLines(PlaceOrderCommand command, Currency currency) {
    List<OrderLine> lines = new ArrayList<>(command.items().size());
    for (PlaceOrderCommand.Item item : command.items()) {
      if (item == null) {
        throw new InvalidOrderException("Order items must not be null");
      }
      lines.add(
          new OrderLine(
              new Sku(item.sku()), item.quantity(), new Money(item.unitPrice(), currency)));
    }
    return lines;
  }

  private static Currency toCurrency(String code) {
    if (code == null) {
      throw new InvalidOrderException("Currency is required");
    }
    try {
      return Currency.getInstance(code);
    } catch (IllegalArgumentException e) {
      throw new InvalidOrderException("Currency must be a valid ISO 4217 code: " + code);
    }
  }

  /** SHA-256 de uma forma canônica do pedido, para comparar requisições com a mesma chave. */
  static String fingerprint(Currency currency, List<OrderLine> lines) {
    StringBuilder canonical = new StringBuilder(currency.getCurrencyCode());
    for (OrderLine line : lines) {
      canonical
          .append('|')
          .append(line.sku().value())
          .append(':')
          .append(line.quantity())
          .append(':')
          .append(line.unitPrice().amount().toPlainString());
    }
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required by every Java platform", e);
    }
  }
}

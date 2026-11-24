package com.caiodassumpcao.orderflow.orders.application;

import com.caiodassumpcao.orderflow.orders.application.port.ConcurrentOrderUpdateException;
import com.caiodassumpcao.orderflow.orders.application.port.IdempotencyKeyStore;
import com.caiodassumpcao.orderflow.orders.application.port.IdempotencyKeyTakenException;
import com.caiodassumpcao.orderflow.orders.application.port.OrderEventOutbox;
import com.caiodassumpcao.orderflow.orders.application.port.OrderRepository;
import com.caiodassumpcao.orderflow.orders.application.port.UnitOfWork;
import com.caiodassumpcao.orderflow.orders.domain.CustomerId;
import com.caiodassumpcao.orderflow.orders.domain.Order;
import com.caiodassumpcao.orderflow.orders.domain.OrderEvent;
import com.caiodassumpcao.orderflow.orders.domain.OrderId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Implementações em memória das portas, com uma unidade de trabalho que desfaz tudo o que foi
 * gravado quando uma exceção escapa (como o banco faria).
 */
final class InMemoryPorts {

  final Orders orders = new Orders();
  final Outbox outbox = new Outbox();
  final Keys keys = new Keys();
  final Transactions transactions = new Transactions();

  private InMemoryPorts() {}

  static InMemoryPorts create() {
    return new InMemoryPorts();
  }

  /** Repositório que guarda cópias reidratadas e incrementa a versão a cada gravação. */
  final class Orders implements OrderRepository {

    private Map<OrderId, Order> rows = new HashMap<>();

    @Override
    public Optional<Order> findById(OrderId id) {
      return Optional.ofNullable(rows.get(id)).map(Orders::copy);
    }

    @Override
    public void save(Order order) {
      Order current = rows.get(order.id());
      long expected = current == null ? -1 : current.version().orElseThrow();
      long actual = order.version().orElse(-1L);
      if (expected != actual) {
        throw new ConcurrentOrderUpdateException(order.id(), null);
      }
      rows.put(order.id(), withVersion(order, actual + 1));
    }

    int count() {
      return rows.size();
    }

    /** Guarda o pedido como se outra transação o tivesse gravado. */
    void seed(Order order) {
      rows.put(order.id(), withVersion(order, order.version().orElse(0L)));
    }

    private static Order copy(Order order) {
      return withVersion(order, order.version().orElseThrow());
    }

    private static Order withVersion(Order order, long version) {
      return Order.rehydrate(
          order.id(),
          order.customerId(),
          order.lines(),
          order.status(),
          order.cancellationReason().orElse(null),
          order.createdAt(),
          order.updatedAt(),
          version);
    }
  }

  /** Outbox que registra os eventos e pode ser configurada para falhar. */
  final class Outbox implements OrderEventOutbox {

    private List<OrderEvent> events = new ArrayList<>();
    private boolean failNext;

    @Override
    public void append(List<OrderEvent> newEvents) {
      if (failNext) {
        failNext = false;
        throw new IllegalStateException("outbox unavailable");
      }
      events.addAll(newEvents);
    }

    List<OrderEvent> events() {
      return List.copyOf(events);
    }

    void failNextAppend() {
      failNext = true;
    }
  }

  /** Chaves por cliente; pode simular uma requisição concorrente que registra a chave antes. */
  final class Keys implements IdempotencyKeyStore {

    private Map<String, StoredKey> rows = new HashMap<>();
    private final Map<String, StoredKey> committedOutsideTransaction = new HashMap<>();
    private boolean loseNextRace;
    private StoredKey raceWinner;

    @Override
    public Optional<StoredKey> find(CustomerId customerId, String key) {
      return Optional.ofNullable(rows.get(id(customerId, key)));
    }

    @Override
    public void save(CustomerId customerId, String key, String requestHash, OrderId orderId) {
      if (loseNextRace) {
        loseNextRace = false;
        if (raceWinner != null) {
          committedOutsideTransaction.put(id(customerId, key), raceWinner);
        }
        throw new IdempotencyKeyTakenException(null);
      }
      if (rows.putIfAbsent(id(customerId, key), new StoredKey(requestHash, orderId)) != null) {
        throw new IdempotencyKeyTakenException(null);
      }
    }

    /**
     * Na próxima gravação, outra transação terá registrado a chave antes. Com {@code winner} nulo,
     * a chave vencedora não fica visível (caso anômalo).
     */
    void loseNextRaceTo(StoredKey winner) {
      loseNextRace = true;
      raceWinner = winner;
    }

    int count() {
      return rows.size();
    }

    private static String id(CustomerId customerId, String key) {
      return customerId.value() + "|" + key;
    }
  }

  /** Desfaz as gravações das três portas se o trabalho lançar exceção. */
  final class Transactions implements UnitOfWork {

    private int committed;

    @Override
    public <T> T inTransaction(Supplier<T> work) {
      Map<OrderId, Order> orderRows = new HashMap<>(orders.rows);
      List<OrderEvent> outboxRows = new ArrayList<>(outbox.events);
      Map<String, IdempotencyKeyStore.StoredKey> keyRows = new HashMap<>(keys.rows);
      try {
        T result = work.get();
        committed++;
        return result;
      } catch (RuntimeException e) {
        orders.rows = orderRows;
        outbox.events = outboxRows;
        keys.rows = keyRows;
        throw e;
      } finally {
        // O que uma transação concorrente confirmou fica visível mesmo após o rollback desta.
        keys.rows.putAll(keys.committedOutsideTransaction);
        keys.committedOutsideTransaction.clear();
      }
    }

    int committed() {
      return committed;
    }
  }
}

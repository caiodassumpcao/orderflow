package com.caiodassumpcao.orderflow.shared.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token bucket por chave (o usuário): até {@code capacity} requisições de uma vez, repostas
 * continuamente ao longo de {@code refillPeriod}. Em memória: vale por instância da API (ver
 * README, Fase 10, para a versão distribuída).
 */
public final class TokenBucketRateLimiter {

  /** Acima disso, chaves ociosas com o balde cheio são descartadas para limitar a memória. */
  static final int MAX_KEYS = 10_000;

  private final int capacity;
  private final double tokensPerNano;
  private final Clock clock;
  private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

  public TokenBucketRateLimiter(int capacity, Duration refillPeriod, Clock clock) {
    if (capacity < 1 || refillPeriod.isZero() || refillPeriod.isNegative()) {
      throw new IllegalArgumentException("capacity and refill period must be positive");
    }
    this.capacity = capacity;
    this.tokensPerNano = capacity / (double) refillPeriod.toNanos();
    this.clock = clock;
  }

  /**
   * Tenta consumir uma requisição da chave.
   *
   * @return a decisão; quando negada, quanto esperar até haver uma ficha
   */
  public Decision tryConsume(String key) {
    long now = nanos();
    if (buckets.size() > MAX_KEYS) {
      evictIdle(now);
    }
    Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(capacity, now));
    synchronized (bucket) {
      bucket.refill(now, capacity, tokensPerNano);
      if (bucket.tokens >= 1) {
        bucket.tokens -= 1;
        return Decision.ALLOWED;
      }
      long waitNanos = (long) Math.ceil((1 - bucket.tokens) / tokensPerNano);
      return Decision.denied(Duration.ofNanos(waitNanos));
    }
  }

  int trackedKeys() {
    return buckets.size();
  }

  private void evictIdle(long now) {
    buckets
        .entrySet()
        .removeIf(entry -> {
          Bucket bucket = entry.getValue();
          synchronized (bucket) {
            bucket.refill(now, capacity, tokensPerNano);
            return bucket.tokens >= capacity;
          }
        });
  }

  private long nanos() {
    Instant now = clock.instant();
    return now.getEpochSecond() * 1_000_000_000L + now.getNano();
  }

  /**
   * Resultado de uma tentativa.
   *
   * @param allowed se a requisição pode seguir
   * @param retryAfter espera sugerida quando negada ({@link Duration#ZERO} quando permitida)
   */
  public record Decision(boolean allowed, Duration retryAfter) {

    static final Decision ALLOWED = new Decision(true, Duration.ZERO);

    static Decision denied(Duration retryAfter) {
      return new Decision(false, retryAfter);
    }

    /** Segundos inteiros para o header {@code Retry-After} (mínimo 1). */
    public long retryAfterSeconds() {
      return Math.max(1, (retryAfter.toMillis() + 999) / 1000);
    }
  }

  private static final class Bucket {

    private double tokens;
    private long updatedAt;

    Bucket(int tokens, long now) {
      this.tokens = tokens;
      this.updatedAt = now;
    }

    void refill(long now, int capacity, double tokensPerNano) {
      long elapsed = Math.max(0, now - updatedAt);
      tokens = Math.min(capacity, tokens + elapsed * tokensPerNano);
      updatedAt = now;
    }
  }
}

package com.caiodassumpcao.orderflow.shared.id;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

/**
 * Gerador de UUID versão 7 (RFC 9562): 48 bits de timestamp em milissegundos seguidos de bits
 * aleatórios. Ids ordenáveis pelo tempo mantêm os índices B-tree compactos.
 */
public final class UuidV7 {

  private static final SecureRandom RANDOM = new SecureRandom();

  private UuidV7() {}

  public static UUID generate() {
    return generate(Clock.systemUTC());
  }

  public static UUID generate(Clock clock) {
    long millis = clock.millis();
    long randA = RANDOM.nextInt(1 << 12);
    long randB = RANDOM.nextLong();
    long mostSignificant = (millis & 0xFFFF_FFFF_FFFFL) << 16 | 0x7000L | randA;
    long leastSignificant = randB & 0x3FFF_FFFF_FFFF_FFFFL | 0x8000_0000_0000_0000L;
    return new UUID(mostSignificant, leastSignificant);
  }
}

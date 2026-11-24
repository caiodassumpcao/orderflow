package com.caiodassumpcao.orderflow.shared.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7Test {

  @Test
  void producesVersion7WithTheRfcVariant() {
    UUID uuid = UuidV7.generate();

    assertThat(uuid.version()).isEqualTo(7);
    assertThat(uuid.variant()).isEqualTo(2);
  }

  @Test
  void encodesTheTimestampInTheFirst48Bits() {
    Instant instant = Instant.parse("2026-11-24T12:34:56.789Z");

    UUID uuid = UuidV7.generate(Clock.fixed(instant, ZoneOffset.UTC));

    assertThat(uuid.getMostSignificantBits() >>> 16).isEqualTo(instant.toEpochMilli());
  }

  @Test
  void sortsByCreationTimeAcrossMilliseconds() {
    Instant earlier = Instant.parse("2026-11-24T12:00:00Z");

    UUID first = UuidV7.generate(Clock.fixed(earlier, ZoneOffset.UTC));
    UUID second = UuidV7.generate(Clock.fixed(earlier.plusMillis(1), ZoneOffset.UTC));

    assertThat(first.toString()).isLessThan(second.toString());
  }

  @Test
  void isUniqueWithinTheSameMillisecond() {
    Clock frozen = Clock.fixed(Instant.now(), ZoneOffset.UTC);
    Set<UUID> ids = new HashSet<>();

    for (int i = 0; i < 10_000; i++) {
      ids.add(UuidV7.generate(frozen));
    }

    assertThat(ids).hasSize(10_000);
  }
}

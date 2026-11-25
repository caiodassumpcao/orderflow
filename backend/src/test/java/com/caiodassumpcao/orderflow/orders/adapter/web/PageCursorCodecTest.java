package com.caiodassumpcao.orderflow.orders.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.caiodassumpcao.orderflow.orders.application.query.PageCursor;
import com.caiodassumpcao.orderflow.orders.domain.OrderId;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PageCursorCodecTest {

  private static String base64(String raw) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void roundTripsTheCursorAsAnUrlSafeToken() {
    PageCursor cursor =
        new PageCursor(
            Instant.parse("2026-11-24T12:34:56.123456Z"), new OrderId(UUID.randomUUID()));

    String token = PageCursorCodec.encode(cursor);

    assertThat(token).matches("^[A-Za-z0-9_-]+$");
    assertThat(PageCursorCodec.decode(token)).isEqualTo(cursor);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"not base64!", "@@@", "   "})
  void rejectsValuesThatAreNotCursors(String value) {
    assertThatThrownBy(() -> PageCursorCodec.decode(value))
        .isInstanceOf(PageCursorCodec.InvalidCursorException.class);
  }

  @Test
  void rejectsTamperedContent() {
    String uuid = UUID.randomUUID().toString();

    for (String raw :
        new String[] {"no-separator", "yesterday|" + uuid, "2026-11-24T12:00:00Z|not-a-uuid"}) {
      assertThatThrownBy(() -> PageCursorCodec.decode(base64(raw)))
          .isInstanceOf(PageCursorCodec.InvalidCursorException.class);
    }
  }

  @Test
  void rejectsOversizedValues() {
    assertThatThrownBy(() -> PageCursorCodec.decode("a".repeat(PageCursorCodec.MAX_LENGTH + 1)))
        .isInstanceOf(PageCursorCodec.InvalidCursorException.class);
  }
}

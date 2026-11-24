package com.caiodassumpcao.orderflow.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

  private final CorrelationIdFilter filter = new CorrelationIdFilter();

  @Test
  void reusesValidIncomingIdAndExposesItInMdcAndResponse() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(CorrelationIdFilter.HEADER, "abc-123-def-456");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<String> mdcDuringRequest = new AtomicReference<>();

    filter.doFilter(
        request,
        response,
        new MockFilterChain() {
          @Override
          public void doFilter(ServletRequest req, ServletResponse res) {
            mdcDuringRequest.set(MDC.get(CorrelationIdFilter.MDC_KEY));
          }
        });

    assertThat(mdcDuringRequest.get()).isEqualTo("abc-123-def-456");
    assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("abc-123-def-456");
  }

  @Test
  void generatesIdWhenHeaderIsAbsentAndClearsMdcAfterRequest() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(new MockHttpServletRequest(), response, new MockFilterChain());

    assertThat(UUID.fromString(response.getHeader(CorrelationIdFilter.HEADER))).isNotNull();
    assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"short", "has space inside", "line\nbreak-injection", "<b>x</b>"})
  void generatesNewUuidWhenIncomingIdIsMissingOrUnsafe(String incoming) {
    String resolved = CorrelationIdFilter.resolve(incoming);

    assertThat(resolved).isNotEqualTo(incoming);
    assertThat(UUID.fromString(resolved)).isNotNull();
  }

  @Test
  void rejectsIdsLongerThan64Characters() {
    String tooLong = "a".repeat(65);

    assertThat(CorrelationIdFilter.resolve(tooLong)).isNotEqualTo(tooLong);
  }
}

package com.procel.api.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {
    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void acceptsValidCorrelationIdAndReturnsHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(CorrelationId.HEADER, "client-correlation-1");
        AtomicReference<String> mdcValue = new AtomicReference<>();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                mdcValue.set(MDC.get(CorrelationId.MDC_KEY)));

        assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo("client-correlation-1");
        assertThat(mdcValue.get()).isEqualTo("client-correlation-1");
        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
    }

    @Test
    void generatesCorrelationIdWhenMissing() throws Exception {
        MockHttpServletResponse response = doFilter(new MockHttpServletRequest());

        assertThat(response.getHeader(CorrelationId.HEADER)).isNotBlank();
    }

    @Test
    void substitutesInvalidCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationId.HEADER, "invalid value with spaces and too much free text");

        MockHttpServletResponse response = doFilter(request);

        assertThat(response.getHeader(CorrelationId.HEADER)).isNotEqualTo("invalid value with spaces and too much free text");
        assertThat(response.getHeader(CorrelationId.HEADER)).matches("[A-Za-z0-9._:-]{1,64}");
    }

    private MockHttpServletResponse doFilter(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}

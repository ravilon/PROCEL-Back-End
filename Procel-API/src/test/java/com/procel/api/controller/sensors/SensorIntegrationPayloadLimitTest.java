package com.procel.api.controller.sensors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.procel.api.config.SensorIntegrationParserProperties;
import com.procel.api.config.SensorIntegrationPayloadLimitFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.DelegatingServletInputStream;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class SensorIntegrationPayloadLimitTest {
    private final SensorIntegrationParserProperties properties = new SensorIntegrationParserProperties();
    private final SensorIntegrationPayloadLimitFilter filter =
            new SensorIntegrationPayloadLimitFilter(properties, new ObjectMapper());

    SensorIntegrationPayloadLimitTest() {
        properties.setMaxPayloadBytes(16);
    }

    @Test
    void rejectsContentLengthAboveLimit() throws Exception {
        MockHttpServletRequest request = integrationRequest("12345678901234567");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, chain(chainCalled, false));

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("PAYLOAD_TOO_LARGE");
        assertThat(chainCalled).isFalse();
    }

    @Test
    void rejectsBodyWithoutContentLengthAboveLimitWhileReading() throws Exception {
        MockHttpServletRequest request = new NoContentLengthRequest("12345678901234567");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, chain(chainCalled, true));

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("PAYLOAD_TOO_LARGE");
        assertThat(chainCalled).isTrue();
    }

    @Test
    void allowsBodyExactlyAtLimit() throws Exception {
        MockHttpServletRequest request = integrationRequest("1234567890123456");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, chain(chainCalled, true));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chainCalled).isTrue();
    }

    @Test
    void allowsBodyExactlyAtDefaultLimit() throws Exception {
        properties.setMaxPayloadBytes(262144);
        MockHttpServletRequest request = integrationRequest("a".repeat(262144));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, chain(chainCalled, true));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chainCalled).isTrue();
    }

    @Test
    void unrelatedEndpointIsNotAffected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/sensors/ingest/mock");
        request.setContent("123456789012345678901234567890".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, chain(chainCalled, false));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chainCalled).isTrue();
    }

    @Test
    void doesNotCloseServletInputStreamPrematurely() throws Exception {
        TrackableInputStream body = new TrackableInputStream("1234567890123456".getBytes(StandardCharsets.UTF_8));
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/api/sensors/ingest/integrations/" + UUID.randomUUID()
        ) {
            @Override
            public ServletInputStream getInputStream() {
                return new DelegatingServletInputStream(body);
            }
        };
        request.setContentType("application/json");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            servletRequest.getInputStream().readAllBytes();
            assertThat(body.closed).isFalse();
            ((MockHttpServletResponse) servletResponse).setStatus(200);
        });

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body.closed).isFalse();
    }

    private MockHttpServletRequest integrationRequest(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/api/sensors/ingest/integrations/" + UUID.randomUUID()
        );
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        request.setContentType("application/json");
        return request;
    }

    private FilterChain chain(AtomicBoolean called, boolean readBody) {
        return new FilterChain() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
                called.set(true);
                if (readBody) {
                    request.getInputStream().readAllBytes();
                }
                ((MockHttpServletResponse) response).setStatus(200);
            }
        };
    }

    private static class NoContentLengthRequest extends MockHttpServletRequest {
        private final byte[] body;

        NoContentLengthRequest(String body) {
            super("POST", "/api/sensors/ingest/integrations/" + UUID.randomUUID());
            this.body = body.getBytes(StandardCharsets.UTF_8);
            setContentType("application/json");
        }

        @Override
        public long getContentLengthLong() {
            return -1;
        }

        @Override
        public int getContentLength() {
            return -1;
        }

        @Override
        public ServletInputStream getInputStream() {
            return new DelegatingServletInputStream(new ByteArrayInputStream(body));
        }
    }

    private static class TrackableInputStream extends InputStream {
        private final ByteArrayInputStream delegate;
        private boolean closed;

        TrackableInputStream(byte[] body) {
            this.delegate = new ByteArrayInputStream(body);
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}

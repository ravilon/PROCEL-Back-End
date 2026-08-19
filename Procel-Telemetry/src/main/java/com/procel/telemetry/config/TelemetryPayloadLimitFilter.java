package com.procel.telemetry.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@Component
public class TelemetryPayloadLimitFilter extends OncePerRequestFilter {
    private final TelemetryProperties properties;
    private final ObjectMapper objectMapper;

    public TelemetryPayloadLimitFilter(TelemetryProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod())
                || !"/api/telemetry/events".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        int limit = properties.getMaxPayloadBytes();
        long contentLength = request.getContentLengthLong();
        if (contentLength > limit) {
            writeTooLarge(response);
            return;
        }
        try {
            filterChain.doFilter(new LimitedRequest(request, limit), response);
        } catch (PayloadTooLargeException ex) {
            if (!response.isCommitted()) {
                writeTooLarge(response);
            }
        }
    }

    private void writeTooLarge(HttpServletResponse response) throws IOException {
        response.setStatus(413);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Map.of(
                "message", "Telemetry payload exceeds max size",
                "error", "PAYLOAD_TOO_LARGE",
                "timestamp", Instant.now().toString()
        ));
    }

    private static class LimitedRequest extends HttpServletRequestWrapper {
        private final int limit;

        LimitedRequest(HttpServletRequest request, int limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new LimitedServletInputStream(super.getInputStream(), limit);
        }
    }

    private static class LimitedServletInputStream extends ServletInputStream {
        private final ServletInputStream delegate;
        private final int limit;
        private int read;

        LimitedServletInputStream(ServletInputStream delegate, int limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value != -1) increment(1);
            return value;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int count = delegate.read(b, off, len);
            if (count > 0) increment(count);
            return count;
        }

        private void increment(int count) throws PayloadTooLargeException {
            read += count;
            if (read > limit) throw new PayloadTooLargeException();
        }
    }

    private static class PayloadTooLargeException extends IOException {}
}

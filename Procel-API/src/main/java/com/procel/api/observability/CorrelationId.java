package com.procel.api.observability;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

public final class CorrelationId {
    public static final String HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";
    private static final int MAX_LENGTH = 64;
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._:-]{1," + MAX_LENGTH + "}");

    private CorrelationId() {
    }

    public static String acceptOrCreate(String value) {
        if (value != null) {
            String candidate = value.trim();
            if (VALID.matcher(candidate).matches()) {
                return candidate;
            }
        }
        return UUID.randomUUID().toString();
    }

    public static String currentOrCreate() {
        String current = MDC.get(MDC_KEY);
        if (current != null && !current.isBlank()) {
            return current;
        }
        String generated = UUID.randomUUID().toString();
        MDC.put(MDC_KEY, generated);
        return generated;
    }
}

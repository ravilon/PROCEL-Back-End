package com.proben.telemetry.observability;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Validates correlation identifiers and keeps the active identifier in SLF4J MDC.
 */

public final class CorrelationId {
    /** HTTP header used to propagate a request correlation identifier. */
    public static final String HEADER = "X-Correlation-ID";
    /** MDC key used to include the correlation identifier in log records. */
    public static final String MDC_KEY = "correlationId";
    private static final int MAX_LENGTH = 64;
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._:-]{1," + MAX_LENGTH + "}");

    private CorrelationId() {
    }

    /** @param value identifier supplied by the caller @return accepted identifier or a new UUID */
    public static String acceptOrCreate(String value) {
        if (value != null) {
            String candidate = value.trim();
            if (VALID.matcher(candidate).matches()) {
                return candidate;
            }
        }
        return UUID.randomUUID().toString();
    }

    /** @return current MDC identifier, creating and storing one when absent */
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

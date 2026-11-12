package com.proben.telemetry.service.canonical;

/**
 * Marks a canonical API failure as temporary and eligible for retry.
 */

public class TransientCanonicalException extends RuntimeException {
    public TransientCanonicalException(String message, Throwable cause) {
        super(message, cause);
    }

    public TransientCanonicalException(String message) {
        super(message);
    }
}

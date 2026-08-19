package com.procel.telemetry.service.canonical;

public class TransientCanonicalException extends RuntimeException {
    public TransientCanonicalException(String message, Throwable cause) {
        super(message, cause);
    }

    public TransientCanonicalException(String message) {
        super(message);
    }
}

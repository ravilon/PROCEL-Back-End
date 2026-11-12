package com.proben.telemetry.service.canonical;

/**
 * Excecao que representa falhas relacionadas a transient canonical.
 */

public class TransientCanonicalException extends RuntimeException {
    public TransientCanonicalException(String message, Throwable cause) {
        super(message, cause);
    }

    public TransientCanonicalException(String message) {
        super(message);
    }
}

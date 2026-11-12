package com.proben.telemetry.service.canonical;

/**
 * Represents a non-success response returned by the canonical API.
 */

public class CanonicalHttpException extends RuntimeException {
    private final int statusCode;
    private final String errorCode;

    /** @param statusCode HTTP status @param errorCode canonical error identifier @param message response detail */
    public CanonicalHttpException(int statusCode, String errorCode, String message) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    /** @return HTTP status returned by the canonical API */
    public int getStatusCode() {
        return statusCode;
    }

    /** @return canonical API error identifier */
    public String getErrorCode() {
        return errorCode;
    }
}

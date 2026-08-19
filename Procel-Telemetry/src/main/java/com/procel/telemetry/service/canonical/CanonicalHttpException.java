package com.procel.telemetry.service.canonical;

public class CanonicalHttpException extends RuntimeException {
    private final int statusCode;
    private final String errorCode;

    public CanonicalHttpException(int statusCode, String errorCode, String message) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

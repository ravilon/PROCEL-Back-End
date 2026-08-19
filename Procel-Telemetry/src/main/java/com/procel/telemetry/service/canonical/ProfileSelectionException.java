package com.procel.telemetry.service.canonical;

public class ProfileSelectionException extends RuntimeException {
    private final String code;

    public ProfileSelectionException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}

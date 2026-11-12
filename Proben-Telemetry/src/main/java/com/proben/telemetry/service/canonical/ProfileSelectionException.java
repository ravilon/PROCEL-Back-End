package com.proben.telemetry.service.canonical;

/**
 * Indicates that a raw event cannot be matched to an active integration profile.
 */

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

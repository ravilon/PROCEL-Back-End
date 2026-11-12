package com.proben.telemetry.service.canonical;

/**
 * Excecao que representa falhas relacionadas a profile selection.
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

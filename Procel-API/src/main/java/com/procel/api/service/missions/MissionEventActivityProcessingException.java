package com.procel.api.service.missions;

public class MissionEventActivityProcessingException extends RuntimeException {
    private final boolean permanent;

    public MissionEventActivityProcessingException(String message, boolean permanent) {
        super(message);
        this.permanent = permanent;
    }

    public MissionEventActivityProcessingException(String message, boolean permanent, Throwable cause) {
        super(message, cause);
        this.permanent = permanent;
    }

    public boolean permanent() {
        return permanent;
    }
}

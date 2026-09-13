package com.procel.api.service.missions.rules.drools;

public class DroolsMissionRuleException extends RuntimeException {
    public DroolsMissionRuleException(String message) {
        super(message);
    }

    public DroolsMissionRuleException(String message, Throwable cause) {
        super(message, cause);
    }
}

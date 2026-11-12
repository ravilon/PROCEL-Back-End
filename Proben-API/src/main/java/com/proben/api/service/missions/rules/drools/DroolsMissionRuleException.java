package com.proben.api.service.missions.rules.drools;

/**
 * Excecao que representa falhas relacionadas a drools mission rule.
 */

public class DroolsMissionRuleException extends RuntimeException {
    public DroolsMissionRuleException(String message) {
        super(message);
    }

    public DroolsMissionRuleException(String message, Throwable cause) {
        super(message, cause);
    }
}

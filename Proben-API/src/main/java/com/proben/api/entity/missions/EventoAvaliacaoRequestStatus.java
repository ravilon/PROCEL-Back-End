package com.proben.api.entity.missions;

/**
 * Componente de evento avaliacao request status usado pelo modulo API.
 */

public enum EventoAvaliacaoRequestStatus {
    PENDING,
    PROCESSING,
    RETRY,
    COMPLETED,
    FAILED,
    IGNORED
}

package com.proben.telemetry.entity;

/**
 * Componente de raw telemetry status usado pelo modulo Telemetry.
 */

public enum RawTelemetryStatus {
    RECEIVED,
    PROCESSING,
    CANONICAL_ACCEPTED,
    CANONICAL_DUPLICATE,
    CANONICAL_CONFLICT,
    CANONICAL_FAILED,
    DISCARDED
}

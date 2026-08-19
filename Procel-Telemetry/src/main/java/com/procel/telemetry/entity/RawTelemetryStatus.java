package com.procel.telemetry.entity;

public enum RawTelemetryStatus {
    RECEIVED,
    PROCESSING,
    CANONICAL_ACCEPTED,
    CANONICAL_DUPLICATE,
    CANONICAL_CONFLICT,
    CANONICAL_FAILED,
    DISCARDED
}

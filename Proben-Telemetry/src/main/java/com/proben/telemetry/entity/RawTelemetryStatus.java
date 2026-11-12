package com.proben.telemetry.entity;

/**
 * Lifecycle states for a raw event as it moves through canonical ingestion.
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

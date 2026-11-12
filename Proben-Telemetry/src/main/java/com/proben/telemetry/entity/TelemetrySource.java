package com.proben.telemetry.entity;

/**
 * Transport or ingestion channel from which a raw telemetry event originated.
 */

public enum TelemetrySource {
    MQTT,
    REST,
    FILE,
    API
}

package com.procel.api.entity.sensors;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sensor_integration_value_mapping")
public class SensorIntegrationValueMapping {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "parser_version_id", nullable = false)
    private SensorIntegrationParserVersion parserVersion;

    @Column(name = "parameter_name", nullable = false, length = 120)
    private String parameterName;

    @Column(name = "value_pointer", nullable = false, length = 500)
    private String valuePointer;

    @Column(nullable = false)
    private boolean required = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected SensorIntegrationValueMapping() {}

    public SensorIntegrationValueMapping(String parameterName, String valuePointer, boolean required) {
        this.parameterName = parameterName;
        this.valuePointer = valuePointer;
        this.required = required;
    }

    public UUID getId() { return id; }
    public SensorIntegrationParserVersion getParserVersion() { return parserVersion; }
    public String getParameterName() { return parameterName; }
    public String getValuePointer() { return valuePointer; }
    public boolean isRequired() { return required; }
    public Instant getCreatedAt() { return createdAt; }
    void setParserVersion(SensorIntegrationParserVersion parserVersion) { this.parserVersion = parserVersion; }
}

package com.procel.api.entity.sensors;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "sensor_integration_parser_version")
public class SensorIntegrationParserVersion {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private SensorIntegrationProfile profile;

    @Column(nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SensorIntegrationParserStatus status = SensorIntegrationParserStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "sensor_resolution_mode", nullable = false, length = 30)
    private SensorResolutionMode sensorResolutionMode;

    @Column(name = "message_id_pointer", nullable = false, length = 500)
    private String messageIdPointer;

    @Column(name = "sensor_external_id_pointer", length = 500)
    private String sensorExternalIdPointer;

    @Column(name = "timestamp_pointer", nullable = false, length = 500)
    private String timestampPointer;

    @Column(name = "source_received_at_pointer", length = 500)
    private String sourceReceivedAtPointer;

    @Column(name = "timestamp_format", nullable = false, length = 80)
    private String timestampFormat = "ISO_INSTANT";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "published_at")
    private Instant publishedAt;

    @OneToMany(mappedBy = "parserVersion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SensorIntegrationValueMapping> valueMappings = new ArrayList<>();

    protected SensorIntegrationParserVersion() {}

    public SensorIntegrationParserVersion(
            SensorIntegrationProfile profile,
            int version,
            SensorResolutionMode sensorResolutionMode,
            String messageIdPointer,
            String sensorExternalIdPointer,
            String timestampPointer,
            String sourceReceivedAtPointer
    ) {
        this.profile = profile;
        this.version = version;
        updateDraft(sensorResolutionMode, messageIdPointer, sensorExternalIdPointer, timestampPointer, sourceReceivedAtPointer);
    }

    public UUID getId() { return id; }
    public SensorIntegrationProfile getProfile() { return profile; }
    public int getVersion() { return version; }
    public SensorIntegrationParserStatus getStatus() { return status; }
    public SensorResolutionMode getSensorResolutionMode() { return sensorResolutionMode; }
    public String getMessageIdPointer() { return messageIdPointer; }
    public String getSensorExternalIdPointer() { return sensorExternalIdPointer; }
    public String getTimestampPointer() { return timestampPointer; }
    public String getSourceReceivedAtPointer() { return sourceReceivedAtPointer; }
    public String getTimestampFormat() { return timestampFormat; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public List<SensorIntegrationValueMapping> getValueMappings() { return valueMappings; }

    public void updateDraft(
            SensorResolutionMode sensorResolutionMode,
            String messageIdPointer,
            String sensorExternalIdPointer,
            String timestampPointer,
            String sourceReceivedAtPointer
    ) {
        this.sensorResolutionMode = sensorResolutionMode;
        this.messageIdPointer = messageIdPointer;
        this.sensorExternalIdPointer = sensorExternalIdPointer;
        this.timestampPointer = timestampPointer;
        this.sourceReceivedAtPointer = sourceReceivedAtPointer;
        this.timestampFormat = "ISO_INSTANT";
        this.updatedAt = Instant.now();
    }

    public void replaceMappings(List<SensorIntegrationValueMapping> mappings) {
        this.valueMappings.clear();
        for (SensorIntegrationValueMapping mapping : mappings) {
            mapping.setParserVersion(this);
            this.valueMappings.add(mapping);
        }
        this.updatedAt = Instant.now();
    }

    public void activate(Instant now) {
        this.status = SensorIntegrationParserStatus.ACTIVE;
        this.publishedAt = now;
        this.updatedAt = now;
    }

    public void inactivate(Instant now) {
        this.status = SensorIntegrationParserStatus.INACTIVE;
        this.updatedAt = now;
    }
}

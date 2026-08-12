package com.procel.api.entity.sensors;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "medicao_ingestao_metadata",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "ux_medicao_ingestao_medicao",
                        columnNames = {"medicao_id"}
                )
        },
        indexes = {
                @Index(name = "idx_medicao_ingestao_sensor_message", columnList = "sensor_external_id,message_id"),
                @Index(name = "idx_medicao_ingestao_status_created", columnList = "status,created_at")
        }
)
public class MedicaoIngestaoMetadata {

    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medicao_id")
    private Medicao medicao;

    @Column(name = "producer_id", nullable = false, length = 80)
    private String producerId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "sensor_external_id", nullable = false, referencedColumnName = "external_id")
    private Sensor sensor;

    @Column(name = "message_id", nullable = false, length = 160)
    private String messageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    private MedicaoIngestaoSource source;

    @Column(name = "source_received_at")
    private Instant sourceReceivedAt;

    @Column(name = "api_received_at", nullable = false)
    private Instant apiReceivedAt;

    @Column(name = "payload_fingerprint", nullable = false, length = 64)
    private String payloadFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private MedicaoIngestaoStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "integration_profile_id")
    private UUID integrationProfileId;

    @Column(name = "parser_version_id")
    private UUID parserVersionId;

    protected MedicaoIngestaoMetadata() {}

    public MedicaoIngestaoMetadata(
            String producerId,
            Sensor sensor,
            String messageId,
            MedicaoIngestaoSource source,
            Instant sourceReceivedAt,
            Instant apiReceivedAt,
            String payloadFingerprint
    ) {
        this.producerId = producerId;
        this.sensor = sensor;
        this.messageId = messageId;
        this.source = source;
        this.sourceReceivedAt = sourceReceivedAt;
        this.apiReceivedAt = apiReceivedAt;
        this.payloadFingerprint = payloadFingerprint;
        this.status = MedicaoIngestaoStatus.PROCESSING;
        this.createdAt = Instant.now();
    }

    public MedicaoIngestaoMetadata(
            String producerId,
            Sensor sensor,
            String messageId,
            MedicaoIngestaoSource source,
            Instant sourceReceivedAt,
            Instant apiReceivedAt,
            String payloadFingerprint,
            UUID integrationProfileId,
            UUID parserVersionId
    ) {
        this(producerId, sensor, messageId, source, sourceReceivedAt, apiReceivedAt, payloadFingerprint);
        this.integrationProfileId = integrationProfileId;
        this.parserVersionId = parserVersionId;
    }

    public UUID getId() { return id; }
    public Medicao getMedicao() { return medicao; }
    public String getProducerId() { return producerId; }
    public Sensor getSensor() { return sensor; }
    public String getMessageId() { return messageId; }
    public MedicaoIngestaoSource getSource() { return source; }
    public Instant getSourceReceivedAt() { return sourceReceivedAt; }
    public Instant getApiReceivedAt() { return apiReceivedAt; }
    public String getPayloadFingerprint() { return payloadFingerprint; }
    public MedicaoIngestaoStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public UUID getIntegrationProfileId() { return integrationProfileId; }
    public UUID getParserVersionId() { return parserVersionId; }

    public void complete(Medicao medicao, Instant completedAt) {
        this.medicao = medicao;
        this.completedAt = completedAt;
        this.status = MedicaoIngestaoStatus.COMPLETED;
    }
}

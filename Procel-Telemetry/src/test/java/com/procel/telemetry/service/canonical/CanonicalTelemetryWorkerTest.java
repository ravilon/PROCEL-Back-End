package com.procel.telemetry.service.canonical;

import com.procel.telemetry.config.TelemetryProperties;
import com.procel.telemetry.entity.RawTelemetryEvent;
import com.procel.telemetry.entity.TelemetrySource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CanonicalTelemetryWorkerTest {
    private RawTelemetryClaimService claimService;
    private SensorIntegrationSnapshotClient snapshotClient;
    private SensorIntegrationProfileSelector profileSelector;
    private CanonicalIngestClient ingestClient;
    private TelemetryProperties properties;
    private CanonicalTelemetryWorker worker;

    @BeforeEach
    void setUp() {
        claimService = mock(RawTelemetryClaimService.class);
        snapshotClient = mock(SensorIntegrationSnapshotClient.class);
        profileSelector = mock(SensorIntegrationProfileSelector.class);
        ingestClient = mock(CanonicalIngestClient.class);
        properties = new TelemetryProperties();
        properties.getCanonicalWorker().setBatchSize(3);
        worker = new CanonicalTelemetryWorker(
                claimService,
                snapshotClient,
                profileSelector,
                ingestClient,
                properties
        );
    }

    @Test
    void emptyBatchStopsWithoutProcessingEvents() {
        when(claimService.claimNext(any(), any())).thenReturn(null);

        int processed = worker.processBatch();

        assertThat(processed).isZero();
        verify(claimService).recoverStuck(any(), any());
        verify(claimService).claimNext(any(), any());
        verifyNoInteractions(snapshotClient, profileSelector, ingestClient);
    }

    @Test
    void processesSequentiallyUntilBatchLimit() {
        properties.getCanonicalWorker().setBatchSize(2);
        RawTelemetryEvent first = event("msg-1");
        RawTelemetryEvent second = event("msg-2");
        RawTelemetryEvent third = event("msg-3");
        var snapshot = snapshot();
        var profile = profile();
        var response = response("MEASUREMENT_INGESTED", "measurement-1");
        when(claimService.claimNext(any(), any())).thenReturn(first, second, third);
        when(snapshotClient.snapshot()).thenReturn(snapshot);
        when(profileSelector.select(any(), eq(snapshot))).thenReturn(profile);
        when(ingestClient.ingest(any(), eq(profile))).thenReturn(response);

        int processed = worker.processBatch();

        assertThat(processed).isEqualTo(2);
        verify(claimService, times(2)).claimNext(any(), any());
        verify(claimService).markAccepted(first, profile, "measurement-1");
        verify(claimService).markAccepted(second, profile, "measurement-1");
        verify(claimService, never()).markAccepted(third, profile, "measurement-1");
    }

    @Test
    void stopsWhenQueueEmptiesBeforeBatchLimit() {
        RawTelemetryEvent first = event("msg-1");
        var snapshot = snapshot();
        var profile = profile();
        when(claimService.claimNext(any(), any())).thenReturn(first, null);
        when(snapshotClient.snapshot()).thenReturn(snapshot);
        when(profileSelector.select(first, snapshot)).thenReturn(profile);
        when(ingestClient.ingest(first, profile)).thenReturn(response("MEASUREMENT_INGESTED", "measurement-1"));

        int processed = worker.processBatch();

        assertThat(processed).isEqualTo(1);
        verify(claimService, times(2)).claimNext(any(), any());
        verify(claimService).markAccepted(first, profile, "measurement-1");
    }

    @Test
    void failureInOneEventDoesNotStopRemainingBatch() {
        RawTelemetryEvent failed = event("msg-failed");
        RawTelemetryEvent accepted = event("msg-accepted");
        var snapshot = snapshot();
        var profile = profile();
        when(claimService.claimNext(any(), any())).thenReturn(failed, accepted, null);
        when(snapshotClient.snapshot()).thenReturn(snapshot);
        when(profileSelector.select(failed, snapshot)).thenThrow(new ProfileSelectionException(
                "PROFILE_NOT_FOUND",
                "No active integration profile matches the raw event."
        ));
        when(profileSelector.select(accepted, snapshot)).thenReturn(profile);
        when(ingestClient.ingest(accepted, profile)).thenReturn(response("MEASUREMENT_INGESTED", "measurement-2"));

        int processed = worker.processBatch();

        assertThat(processed).isEqualTo(2);
        verify(claimService).markFailed(failed, "PROFILE_NOT_FOUND");
        verify(claimService).markAccepted(accepted, profile, "measurement-2");
    }

    @Test
    void unexpectedFailureInOneEventDoesNotStopRemainingBatch() {
        RawTelemetryEvent failed = event("msg-failed");
        RawTelemetryEvent accepted = event("msg-accepted");
        var snapshot = snapshot();
        var profile = profile();
        when(claimService.claimNext(any(), any())).thenReturn(failed, accepted, null);
        when(snapshotClient.snapshot()).thenReturn(snapshot);
        doThrow(new IllegalStateException("boom")).when(profileSelector).select(failed, snapshot);
        when(profileSelector.select(accepted, snapshot)).thenReturn(profile);
        when(ingestClient.ingest(accepted, profile)).thenReturn(response("MEASUREMENT_INGESTED", "measurement-2"));

        int processed = worker.processBatch();

        assertThat(processed).isEqualTo(2);
        verify(claimService).retryOrFail(eq(failed), eq("WORKER_UNEXPECTED_ERROR"), any());
        verify(claimService).markAccepted(accepted, profile, "measurement-2");
    }

    private static RawTelemetryEvent event(String messageId) {
        return new RawTelemetryEvent(
                "producer-1",
                TelemetrySource.REST,
                messageId,
                "sensor-1",
                Instant.parse("2026-08-19T11:59:59Z"),
                Instant.parse("2026-08-19T12:00:00Z"),
                Map.of("value", 1),
                messageId,
                Instant.parse("2026-08-20T12:00:00Z")
        );
    }

    private static CanonicalApiDTOs.SnapshotResponse snapshot() {
        return new CanonicalApiDTOs.SnapshotResponse(1, Instant.parse("2026-08-19T12:00:00Z"), List.of(profile()));
    }

    private static CanonicalApiDTOs.ProfileSnapshot profile() {
        return new CanonicalApiDTOs.ProfileSnapshot(
                "profile-1",
                "Profile 1",
                TelemetrySource.REST,
                new CanonicalApiDTOs.ParserVersionSnapshot(
                        "parser-version-1",
                        1,
                        CanonicalApiDTOs.SensorResolutionMode.ROUTE_SENSOR,
                        "/messageId",
                        "/sensorId",
                        "/timestamp",
                        null,
                        null,
                        List.of()
                ),
                List.of(new CanonicalApiDTOs.BindingSnapshot("sensor-1", "Sensor 1"))
        );
    }

    private static CanonicalApiDTOs.CanonicalIngestResponse response(String code, String measurementId) {
        return new CanonicalApiDTOs.CanonicalIngestResponse(
                "accepted",
                code,
                false,
                measurementId,
                "msg-1",
                Instant.parse("2026-08-19T12:00:01Z"),
                null,
                null,
                null,
                null
        );
    }
}

package com.procel.telemetry.service.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.procel.telemetry.config.TelemetryProperties;
import com.procel.telemetry.dto.TelemetryEventDTOs;
import com.procel.telemetry.entity.RawTelemetryStatus;
import com.procel.telemetry.observability.TelemetryObservabilityMetrics;
import com.procel.telemetry.repository.RawTelemetryEventRepository;
import com.procel.telemetry.service.TelemetryIngestService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MqttTelemetryMessageHandlerTest {
    @Test
    void transientMongoFailureIsNotAcknowledgedByHandler() {
        TelemetryIngestService ingestService = mock(TelemetryIngestService.class);
        when(ingestService.ingest(eq("producer-a"), any()))
                .thenThrow(new DataAccessResourceFailureException("mongo unavailable"));
        MqttTelemetryMessageHandler handler = handler(ingestService);

        MqttTelemetryMessageHandler.HandlingDecision decision = handler.handle(
                "procel/telemetry/v1/producer-a/sensor-1/events",
                message("""
                        {"messageId":"msg-1","payload":{"value":1}}
                        """)
        );

        assertThat(decision).isEqualTo(MqttTelemetryMessageHandler.HandlingDecision.RETRY);
        assertThat(handler.counters().transientFailures()).isEqualTo(1);
    }

    @Test
    void invalidJsonIsPermanentAndAcknowledgedByHandler() {
        MqttTelemetryMessageHandler handler = handler(mock(TelemetryIngestService.class));

        MqttTelemetryMessageHandler.HandlingDecision decision = handler.handle(
                "procel/telemetry/v1/producer-a/sensor-1/events",
                message("{")
        );

        assertThat(decision).isEqualTo(MqttTelemetryMessageHandler.HandlingDecision.ACK);
        assertThat(handler.counters().discarded()).isEqualTo(1);
    }

    @Test
    void invalidTopicIsPermanentAndAcknowledgedByHandler() {
        MqttTelemetryMessageHandler handler = handler(mock(TelemetryIngestService.class));

        MqttTelemetryMessageHandler.HandlingDecision decision = handler.handle(
                "procel/telemetry/v1/events",
                message("""
                        {"messageId":"msg-1","payload":{"value":1}}
                        """)
        );

        assertThat(decision).isEqualTo(MqttTelemetryMessageHandler.HandlingDecision.ACK);
        assertThat(handler.counters().discarded()).isEqualTo(1);
    }

    @Test
    void producerIdComesExclusivelyFromTopic() {
        TelemetryIngestService ingestService = mock(TelemetryIngestService.class);
        when(ingestService.ingest(eq("producer-topic"), any())).thenReturn(new TelemetryEventDTOs.IngestResponse(
                "raw-1",
                RawTelemetryStatus.RECEIVED,
                false,
                Instant.parse("2026-08-19T12:00:00Z")
        ));
        MqttTelemetryMessageHandler handler = handler(ingestService);

        MqttTelemetryMessageHandler.HandlingDecision decision = handler.handle(
                "procel/telemetry/v1/producer-topic/sensor-1/events",
                message("""
                        {"producerId":"producer-body","messageId":"msg-1","payload":{"value":1}}
                        """)
        );

        assertThat(decision).isEqualTo(MqttTelemetryMessageHandler.HandlingDecision.ACK);
        verify(ingestService).ingest(eq("producer-topic"), any());
    }

    private static MqttTelemetryMessageHandler handler(TelemetryIngestService ingestService) {
        TelemetryProperties properties = new TelemetryProperties();
        ObjectMapper objectMapper = new ObjectMapper();
        return new MqttTelemetryMessageHandler(
                properties,
                new MqttTopicParser(),
                new MqttPayloadAdapter(objectMapper),
                ingestService,
                new TelemetryObservabilityMetrics(new SimpleMeterRegistry(), mock(RawTelemetryEventRepository.class))
        );
    }

    private static MqttMessage message(String json) {
        MqttMessage message = new MqttMessage(json.getBytes(StandardCharsets.UTF_8));
        message.setQos(1);
        return message;
    }
}

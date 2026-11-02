package com.procel.api.dto.shared;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SharedContextDTOs {
    private SharedContextDTOs() {}
    public record ContextRequest(String nome, String descricao, Boolean ativo, String locationMode, String campusNome,
                                 BigDecimal latitude, BigDecimal longitude, String timezone) {}
    public record ContextResponse(UUID id, String nome, String descricao, boolean ativo, BigDecimal latitude,
                                  BigDecimal longitude, String timezone, String locationMode, String campusNome,
                                  BigDecimal resolvedLatitude, BigDecimal resolvedLongitude, List<String> compartimentoIds) {}
    public record RoomsRequest(List<String> compartimentoIds) {}
    public record SourceSelectionRequest(UUID parametroDefId, String sensorExternalId, Integer validadeSegundos) {}
    public record IntegrationRequest(String sensorExternalId, String nome, Boolean ativo, String endpointUrl,
                                     String metodo, String requestBody, String authMode, String credential, String credentialHeader,
                                     String credentialQueryParam, String coordinateQueryParam, String queryParamsJson,
                                     String temperatureJsonPointer, String observedAtJsonPointer,
                                     String observedAtFormat, String sourceIdJsonPointer, Integer intervalSeconds, Integer timeoutMillis,
                                     Integer maxAttempts) {}
    public record IntegrationResponse(UUID id, UUID contextoId, String sensorExternalId, String nome, boolean ativo,
                                      String endpointUrl, String metodo, String authMode, boolean credentialConfigured,
                                      String credentialHeader, String credentialQueryParam, String coordinateQueryParam, String queryParamsJson,
                                      String temperatureJsonPointer, String observedAtJsonPointer,
                                      String observedAtFormat, String sourceIdJsonPointer, int intervalSeconds, int timeoutMillis,
                                      int maxAttempts, Instant nextRunAt, Instant lastSuccessAt, String lastOutcome,
                                      Instant lastObservationAt, String lastError) {}
    public record BatchSourceSelectionRequest(List<String> compartimentoIds, UUID parametroDefId,
                                              String sensorExternalId, Integer validadeSegundos) {}
    public record CollectionTestResponse(String outcome, Instant observationAt, BigDecimal value, boolean persisted) {}
    public record ObservationResponse(String status, BigDecimal value, String unit, Instant observedAt,
                                      String sensorExternalId, String source, Instant validUntil) {}
    public record SevenDayAverageResponse(String status, BigDecimal average, String unit, Instant windowStart,
                                           Instant windowEnd, String timezone, BigDecimal coveredHours,
                                           BigDecimal requiredHours, int distinctObservations, String method) {}
}

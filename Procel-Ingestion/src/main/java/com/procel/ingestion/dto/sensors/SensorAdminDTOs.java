package com.procel.ingestion.dto.sensors;

import com.procel.ingestion.entity.sensors.DataType;

import java.util.List;
import java.util.UUID;

public final class SensorAdminDTOs {

    private SensorAdminDTOs() {}

    public record TipoSensorRequest(String nome) {}

    public record TipoSensorUpdateRequest(String nome) {}

    public record TipoSensorResponse(String nome, List<ParametroResponse> parametros) {}

    public record ParametroRequest(
            String nome,
            String descricao,
            DataType dataType,
            String numericUnit
    ) {}

    public record ParametroResponse(
            UUID id,
            String tipoNome,
            String nome,
            String descricao,
            DataType dataType,
            String numericUnit
    ) {}

    public record SensorRequest(
            String externalId,
            String nome,
            String tipoNome,
            String compartimentoId
    ) {}

    public record SensorResponse(
            String externalId,
            String nome,
            String tipoNome,
            String compartimentoId,
            String compartimentoNome
    ) {}
}

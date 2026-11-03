package com.proben.api.dto.sensors;

import com.proben.api.entity.sensors.DataType;

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
            String numericUnit,
            boolean ativo
    ) {}

    public record SensorRequest(
            String externalId,
            String nome,
            String tipoNome,
            String compartimentoId,
            UUID contextoCompartilhadoId
    ) {
        public SensorRequest(String externalId, String nome, String tipoNome, String compartimentoId) {
            this(externalId, nome, tipoNome, compartimentoId, null);
        }
    }

    public record SensorResponse(
            String externalId,
            String nome,
            String tipoNome,
            String compartimentoId,
            String compartimentoNome,
            UUID contextoCompartilhadoId,
            String contextoCompartilhadoNome,
            boolean ativo
    ) {
        public SensorResponse(String externalId, String nome, String tipoNome, String compartimentoId,
                              String compartimentoNome, boolean ativo) {
            this(externalId, nome, tipoNome, compartimentoId, compartimentoNome, null, null, ativo);
        }
    }
}

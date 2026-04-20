package com.procel.ingestion.dto.sensors;

import com.procel.ingestion.entity.sensors.AvaliacaoResultado;
import com.procel.ingestion.entity.sensors.RegraOperador;
import com.procel.ingestion.entity.sensors.SensorGrupoRegraStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class RegraDTOs {

    public record GrupoRegraRequest(
            @Schema(example = "Conforto termico - Sala 2")
            String nome,
            String descricao,
            Boolean ativo
    ) {}

    public record GrupoRegraResponse(
            UUID id,
            String nome,
            String descricao,
            boolean ativo,
            Instant createdAt
    ) {}

    public record RegraParametroRequest(
            UUID parametroDefId,
            @Schema(example = "Temperatura critica")
            String nome,
            String descricao,
            RegraOperador operador,
            BigDecimal valorNumeric1,
            BigDecimal valorNumeric2,
            String valorText,
            Boolean valorBoolean,
            AvaliacaoResultado resultado,
            Integer severidade,
            Integer prioridade,
            Boolean ativo
    ) {}

    public record RegraParametroResponse(
            UUID id,
            UUID grupoRegraId,
            UUID parametroDefId,
            String parametroNome,
            String nome,
            String descricao,
            RegraOperador operador,
            BigDecimal valorNumeric1,
            BigDecimal valorNumeric2,
            String valorText,
            Boolean valorBoolean,
            AvaliacaoResultado resultado,
            int severidade,
            int prioridade,
            boolean ativo,
            Instant createdAt
    ) {}

    public record SensorGrupoRegraRequest(
            UUID grupoRegraId,
            SensorGrupoRegraStatus status,
            Instant validoDe,
            Instant validoAte
    ) {}

    public record SensorGrupoRegraResponse(
            UUID id,
            String sensorExternalId,
            UUID grupoRegraId,
            String grupoRegraNome,
            SensorGrupoRegraStatus status,
            Instant validoDe,
            Instant validoAte,
            Instant createdAt
    ) {}

    public record ParametroDefResponse(
            UUID id,
            String tipoNome,
            String nome,
            String descricao,
            String dataType,
            String numericUnit
    ) {}
}

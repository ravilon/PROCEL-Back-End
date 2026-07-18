package com.procel.ingestion.service.sensors;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Parametros para geracao mockada de medicoes.")
public record MockIngestRequest(
        @Schema(description = "Sensor alvo. Se omitido, usa o primeiro sensor disponivel.", example = "SII-001")
        String sensorExternalId,
        @Schema(description = "Janela retroativa, em minutos. Padrao: 60.", example = "10")
        Integer minutesBack,
        @Schema(description = "Intervalo entre medicoes geradas, em segundos. Padrao: 10.", example = "10")
        Integer everySeconds,
        @Schema(description = "Origem gravada nas medicoes. Padrao: mock.", example = "mock")
        String source
) {}

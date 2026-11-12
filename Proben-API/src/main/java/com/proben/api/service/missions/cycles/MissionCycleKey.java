package com.proben.api.service.missions.cycles;

import com.proben.api.entity.missions.MissaoCicloTipo;

import java.time.Instant;

/**
 * Componente de mission cycle key usado pelo modulo API.
 */

public record MissionCycleKey(
        String chave,
        MissaoCicloTipo cicloTipo,
        Instant inicio,
        Instant fim
) {
}

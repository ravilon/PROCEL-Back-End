package com.proben.api.service.missions.cycles;

import com.proben.api.entity.missions.MissaoCicloTipo;

import java.time.Instant;

public record MissionCycleKey(
        String chave,
        MissaoCicloTipo cicloTipo,
        Instant inicio,
        Instant fim
) {
}

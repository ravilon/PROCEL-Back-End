package com.proben.api.service.missions.cycles;

import com.proben.api.entity.missions.MissaoCicloTipo;

import java.time.Instant;

/**
 * Chave do ciclo de missão e respectivo intervalo temporal.
 *
 * @param chave valor usado para identificar a instância do ciclo
 * @param cicloTipo periodicidade que determina o ciclo
 * @param inicio início do intervalo do ciclo
 * @param fim fim do intervalo do ciclo
 */

public record MissionCycleKey(
        String chave,
        MissaoCicloTipo cicloTipo,
        Instant inicio,
        Instant fim
) {
}

package com.proben.api.service.rooms;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Estado e resultado observável de uma execução assíncrona de sincronização de aulas.
 *
 * @param jobId identificador da execução
 * @param status estado atual da execução
 * @param weekStart primeiro dia do intervalo solicitado
 * @param roomId sala solicitada, quando a execução é restrita a uma sala
 * @param createdAt instante de criação do job
 * @param startedAt instante de início do processamento, se já iniciado
 * @param completedAt instante de conclusão, se finalizado
 * @param progress contadores de progresso da sincronização
 * @param result resumo final da sincronização, quando disponível
 * @param error mensagem de falha, quando a execução falhar
 */

public record AulasSyncJobResponse(
        UUID jobId,
        AulasSyncJobStatus status,
        LocalDate weekStart,
        String roomId,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        AulasSyncProgress progress,
        AulasSyncResult result,
        String error
) {}

package com.proben.api.service.rooms;

import java.time.LocalDate;
import java.util.List;

/**
 * Resumo quantitativo do resultado de uma sincronização semanal de aulas.
 *
 * @param weekStart início do intervalo sincronizado
 * @param weekEnd fim do intervalo sincronizado
 * @param roomsRequested quantidade de salas solicitadas
 * @param roomsSynced quantidade de salas sincronizadas sem erro
 * @param roomsFailed quantidade de salas cuja sincronização falhou
 * @param occurrencesFetched ocorrências de aula recebidas da origem
 * @param occurrencesDeleted ocorrências anteriores removidas ou substituídas
 * @param occurrencesInserted ocorrências gravadas no intervalo
 * @param disciplinesCreated disciplinas novas cadastradas
 * @param disciplinesUpdated disciplinas existentes atualizadas
 * @param elapsedMs duração total da execução em milissegundos
 * @param errors mensagens de erro associadas às salas que falharam
 */

public record AulasSyncResult(
        LocalDate weekStart,
        LocalDate weekEnd,
        int roomsRequested,
        int roomsSynced,
        int roomsFailed,
        int occurrencesFetched,
        int occurrencesDeleted,
        int occurrencesInserted,
        int disciplinesCreated,
        int disciplinesUpdated,
        long elapsedMs,
        List<String> errors
) {}

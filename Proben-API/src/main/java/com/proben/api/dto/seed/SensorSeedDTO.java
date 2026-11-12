package com.proben.api.dto.seed;

/**
 * Sensor definido no arquivo de carga inicial do catálogo.
 *
 * @param externalId identificador externo usado para vincular o sensor às mensagens
 * @param nome nome de exibição do sensor
 * @param tipoNome nome do tipo de sensor cadastrado
 * @param compartimentoId identificador do compartimento associado
 */

public record SensorSeedDTO(
        String externalId,
        String nome,
        String tipoNome,
        String compartimentoId
) {}

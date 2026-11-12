package com.proben.api.dto.seed;

import java.util.List;

/**
 * Conteúdo do arquivo de configuração usado para inicializar sensores.
 *
 * @param sensors sensores declarados para a carga inicial
 */

public record SensorsSeedFile(List<SensorSeedDTO> sensors) {}

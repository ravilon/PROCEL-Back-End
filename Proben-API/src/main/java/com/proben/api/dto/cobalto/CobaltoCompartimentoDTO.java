package com.proben.api.dto.cobalto;

/**
 * Objeto de transferencia de dados usado em cobalto compartimento.
 */

public record CobaltoCompartimentoDTO(
        Long compartimento_id,
        String compartimento_nome,
        String lotacao,
        Integer compartimento_pavimento,
        String compartimento_area,
        Integer compartimento_capacidade,
        String predio_nome,
        String campus_nome,
        String utilizacao_compartimento_descricao,
        String unidade_nome
) {}
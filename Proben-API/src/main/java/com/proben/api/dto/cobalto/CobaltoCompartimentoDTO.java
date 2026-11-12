package com.proben.api.dto.cobalto;

/**
 * Representação de um compartimento recebida da integração Cobalto.
 *
 * @param compartimento_id identificador do compartimento na origem
 * @param compartimento_nome nome do compartimento
 * @param lotacao valor de lotação informado pela origem
 * @param compartimento_pavimento pavimento em que o compartimento está localizado
 * @param compartimento_area área informada pela origem
 * @param compartimento_capacidade capacidade máxima do compartimento
 * @param predio_nome nome do prédio
 * @param campus_nome nome do campus
 * @param utilizacao_compartimento_descricao descrição do uso do compartimento
 * @param unidade_nome nome da unidade acadêmica
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

package com.procel.api.integration.cobalto;

import com.procel.api.dto.cobalto.CobaltoCompartimentoDTO;
import com.procel.api.service.rooms.RoomRecord;
import com.procel.api.service.rooms.TextNorm;

public final class CobaltoMapper {
    private CobaltoMapper() {}

    public static RoomRecord toRoom(CobaltoCompartimentoDTO dto) {
        return new RoomRecord(
                dto.compartimento_id(),
                TextNorm.norm(dto.campus_nome()),
                TextNorm.norm(dto.predio_nome()),
                TextNorm.norm(dto.unidade_nome()),
                TextNorm.norm(dto.compartimento_nome()),
                TextNorm.norm(dto.utilizacao_compartimento_descricao()),
                dto.compartimento_pavimento(),
                dto.compartimento_capacidade(),
                TextNorm.toBigDecimalOrNull(dto.compartimento_area()),
                TextNorm.norm(dto.lotacao())
        );
    }
}
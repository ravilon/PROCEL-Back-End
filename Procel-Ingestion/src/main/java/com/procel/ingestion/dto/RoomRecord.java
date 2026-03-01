package com.procel.ingestion.dto;
import java.math.BigDecimal;

public record RoomRecord(
    long externalId,
    String campusNome,
    String predioNome,
    String salaNome,
    Integer pavimento,
    BigDecimal area,
    Integer capacidade,
    String utilizacao,
    Integer lotacaoHoras,
    String unidadeNome
) {}
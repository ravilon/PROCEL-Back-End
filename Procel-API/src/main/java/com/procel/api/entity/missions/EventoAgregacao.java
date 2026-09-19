package com.procel.api.entity.missions;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum EventoAgregacao {
    ULTIMO,
    PRIMEIRO,
    MIN,
    MAX,
    MEDIA,
    SOMA,
    CONTAGEM,
    TEMPO_VERDADEIRO,
    DELTA;

    @JsonCreator
    public static EventoAgregacao fromJson(String value) {
        if (value == null) return null;
        return switch (value.trim().toUpperCase()) {
            case "ULTIMO_VALOR" -> ULTIMO;
            case "MINIMO" -> MIN;
            case "MAXIMO" -> MAX;
            default -> valueOf(value.trim().toUpperCase());
        };
    }
}
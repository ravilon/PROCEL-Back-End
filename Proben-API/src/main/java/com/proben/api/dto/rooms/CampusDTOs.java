package com.proben.api.dto.rooms;
import java.math.BigDecimal;
import java.util.List;
public final class CampusDTOs {
    private CampusDTOs() {}
    public record CampusRequest(BigDecimal latitude, BigDecimal longitude) {}
    public record CampusResponse(String nome, BigDecimal latitude, BigDecimal longitude, List<String> contextoNomes) {}
}

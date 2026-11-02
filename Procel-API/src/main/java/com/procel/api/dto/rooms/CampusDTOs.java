package com.procel.api.dto.rooms;
import java.math.BigDecimal;
public final class CampusDTOs {
    private CampusDTOs() {}
    public record CampusRequest(BigDecimal latitude, BigDecimal longitude) {}
    public record CampusResponse(String nome, BigDecimal latitude, BigDecimal longitude) {}
}

package com.proben.api.service.environment;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Componente de adaptive comfort calculator usado pelo modulo API.
 */

public final class AdaptiveComfortCalculator {
    private AdaptiveComfortCalculator() {}

    public static ComfortBand calculate(BigDecimal externalSevenDayAverage,
                                        BigDecimal offsetLower,
                                        BigDecimal offsetUpper) {
        if (externalSevenDayAverage == null) throw new IllegalArgumentException("Temed is required");
        BigDecimal neutral = new BigDecimal("17.8")
                .add(new BigDecimal("0.31").multiply(externalSevenDayAverage))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal lower = neutral.subtract(offsetLower == null ? new BigDecimal("3.5") : offsetLower);
        BigDecimal upper = neutral.add(offsetUpper == null ? new BigDecimal("3.5") : offsetUpper);
        return new ComfortBand(neutral, lower, upper);
    }

    public record ComfortBand(BigDecimal neutral, BigDecimal lower, BigDecimal upper) {}
}

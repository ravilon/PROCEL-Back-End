package com.proben.api.service.environment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptiveComfortCalculatorTest {
    @Test
    void computesConfiguredAdaptiveComfortBand() {
        var band = AdaptiveComfortCalculator.calculate(new BigDecimal("20"),
                new BigDecimal("3.5"), new BigDecimal("3.5"));
        assertThat(band.neutral()).isEqualByComparingTo("24.00");
        assertThat(band.lower()).isEqualByComparingTo("20.50");
        assertThat(band.upper()).isEqualByComparingTo("27.50");
    }

    @Test
    void appliesTheNeutralTemperatureFormulaExactly() {
        var band = AdaptiveComfortCalculator.calculate(new BigDecimal("10"),
                new BigDecimal("3.5"), new BigDecimal("3.5"));
        assertThat(band.neutral()).isEqualByComparingTo("20.90");
        assertThat(band.lower()).isEqualByComparingTo("17.40");
        assertThat(band.upper()).isEqualByComparingTo("24.40");
    }

    @Test
    void definesBothComfortLimitsAsInclusive() {
        var band = AdaptiveComfortCalculator.calculate(new BigDecimal("20"),
                new BigDecimal("3.5"), new BigDecimal("3.5"));
        assertThat(band.lower().compareTo(band.lower())).isZero();
        assertThat(band.upper().compareTo(band.upper())).isZero();
        assertThat(band.lower().compareTo(band.lower().subtract(new BigDecimal("0.01")))).isPositive();
        assertThat(band.upper().compareTo(band.upper().add(new BigDecimal("0.01")))).isNegative();
    }
}

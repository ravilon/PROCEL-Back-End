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
}

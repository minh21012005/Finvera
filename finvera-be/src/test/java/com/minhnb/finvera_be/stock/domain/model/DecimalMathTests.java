package com.minhnb.finvera_be.stock.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DecimalMathTests {

    @Test
    void dividesAtScaleTwelveWithHalfUpRounding() {
        BigDecimal result = DecimalMath.divide12(new BigDecimal("100"), new BigDecimal("3"));
        assertThat(result).isEqualByComparingTo("33.333333333333");
        assertThat(result.scale()).isEqualTo(12);
    }

    @Test
    void divisionByZeroReturnsNullRatherThanThrowing() {
        assertThat(DecimalMath.divide12(new BigDecimal("100"), BigDecimal.ZERO)).isNull();
    }

    @Test
    void sqrtIsComputedAtScaleTwelve() {
        BigDecimal result = DecimalMath.sqrt12(new BigDecimal("2"));
        assertThat(result).isEqualByComparingTo("1.414213562373");
        assertThat(result.scale()).isEqualTo(12);
    }

    @Test
    void sqrtOfNegativeValueThrows() {
        assertThatThrownBy(() -> DecimalMath.sqrt12(new BigDecimal("-1")))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void displayRoundingHappensOnceAtThePresentationBoundary() {
        assertThat(DecimalMath.roundForDisplay(new BigDecimal("123.455000000000"), 2))
                .isEqualByComparingTo("123.46");
        assertThat(DecimalMath.roundForDisplay(new BigDecimal("0"), 2)).isEqualByComparingTo("0.00");
    }

    @Test
    void displayedScoreBoundariesMatchValuationV1WorkedExample() {
        assertThat(DecimalMath.roundToDisplayedScore(new BigDecimal("35.500000000000"))).isEqualTo(36);
        assertThat(DecimalMath.roundToDisplayedScore(new BigDecimal("64.400000000000"))).isEqualTo(64);
        assertThat(DecimalMath.roundToDisplayedScore(new BigDecimal("64.500000000000"))).isEqualTo(65);
    }
}

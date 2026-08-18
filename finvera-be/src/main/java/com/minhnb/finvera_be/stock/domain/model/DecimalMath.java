package com.minhnb.finvera_be.stock.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Shared scale-12 {@code HALF_UP} decimal arithmetic for
 * {@code technical-indicators-v1} rule U-3 and {@code valuation-v1} rule U-1:
 * every division is rounded to scale 12 immediately, and no intermediate
 * value is rounded to display precision. Addition, subtraction, and
 * multiplication stay exact (no rounding) until the next division or the
 * final display rounding.
 */
public final class DecimalMath {

    public static final int INTERMEDIATE_SCALE = 12;

    private DecimalMath() {
    }

    /** Divides at scale 12, {@code HALF_UP}; {@code null} when the divisor is zero. */
    public static BigDecimal divide12(BigDecimal numerator, BigDecimal denominator) {
        Objects.requireNonNull(numerator, "numerator");
        Objects.requireNonNull(denominator, "denominator");
        if (denominator.signum() == 0) {
            return null;
        }
        return numerator.divide(denominator, INTERMEDIATE_SCALE, RoundingMode.HALF_UP);
    }

    /** Population-variance square root at scale 12, {@code HALF_UP}. */
    public static BigDecimal sqrt12(BigDecimal nonNegativeValue) {
        Objects.requireNonNull(nonNegativeValue, "nonNegativeValue");
        if (nonNegativeValue.signum() < 0) {
            throw new ArithmeticException("Cannot take the square root of a negative value");
        }
        return nonNegativeValue.sqrt(new java.math.MathContext(50))
                .setScale(INTERMEDIATE_SCALE, RoundingMode.HALF_UP);
    }

    /** Display rounding, applied exactly once at the presentation boundary (rule U-4). */
    public static BigDecimal roundForDisplay(BigDecimal unroundedValue, int displayScale) {
        Objects.requireNonNull(unroundedValue, "unroundedValue");
        if (displayScale < 0) {
            throw new IllegalArgumentException("displayScale must be non-negative");
        }
        return unroundedValue.setScale(displayScale, RoundingMode.HALF_UP);
    }

    /** Nearest displayed integer for a 0-100 score, {@code HALF_UP} (valuation-v1). */
    public static int roundToDisplayedScore(BigDecimal unroundedScore) {
        Objects.requireNonNull(unroundedScore, "unroundedScore");
        return unroundedScore.setScale(0, RoundingMode.HALF_UP).intValueExact();
    }
}

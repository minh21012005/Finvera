package com.minhnb.finvera_be.market.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record DecimalValue(
        BigDecimal value,
        String unit,
        int scale,
        RoundingMode roundingMode) {

    public DecimalValue {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(roundingMode, "roundingMode");
        if (unit.isBlank()) {
            throw new IllegalArgumentException("unit must not be blank");
        }
        if (scale < 0) {
            throw new IllegalArgumentException("scale must be non-negative");
        }
        value = value.setScale(scale, roundingMode);
    }

    public static DecimalValue exact(BigDecimal value, String unit, int scale) {
        return new DecimalValue(value, unit, scale, RoundingMode.UNNECESSARY);
    }

    public static DecimalValue rounded(
            BigDecimal value,
            String unit,
            int scale,
            RoundingMode roundingMode) {
        if (roundingMode == RoundingMode.UNNECESSARY) {
            throw new IllegalArgumentException("rounded values require an explicit rounding policy");
        }
        return new DecimalValue(value, unit, scale, roundingMode);
    }
}

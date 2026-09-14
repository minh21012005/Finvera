package com.minhnb.finvera_be.backtest.domain;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Phép toán thập phân xác định dùng cho các chỉ số backtest.
 *
 * <p>Thuật toán chia đôi có số vòng lặp cố định, vì vậy kết quả không phụ thuộc
 * vào thư viện toán dấu phẩy động hoặc kiến trúc CPU.</p>
 */
final class DeterministicDecimalMath {
    private static final int BISECTION_STEPS = 512;
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private DeterministicDecimalMath() {
    }

    static BigDecimal sqrt(BigDecimal value, MathContext context) {
        return nthRoot(value, 2, context);
    }

    static BigDecimal rationalPower(BigDecimal value, int numerator, int denominator, MathContext context) {
        if (numerator < 0 || denominator <= 0) {
            throw new IllegalArgumentException("INVALID_RATIONAL_EXPONENT");
        }
        if (numerator == 0) {
            return BigDecimal.ONE;
        }
        return nthRoot(value, denominator, context).pow(numerator, context);
    }

    private static BigDecimal nthRoot(BigDecimal value, int root, MathContext context) {
        if (value == null || value.signum() < 0 || root <= 0) {
            throw new IllegalArgumentException("INVALID_ROOT_INPUT");
        }
        if (value.signum() == 0 || value.compareTo(BigDecimal.ONE) == 0 || root == 1) {
            return value.round(context);
        }

        BigDecimal low = value.compareTo(BigDecimal.ONE) < 0 ? value : BigDecimal.ONE;
        BigDecimal high = value.compareTo(BigDecimal.ONE) < 0 ? BigDecimal.ONE : value;
        for (int step = 0; step < BISECTION_STEPS; step++) {
            BigDecimal midpoint = low.add(high, context).divide(TWO, context);
            int comparison = midpoint.pow(root, context).compareTo(value);
            if (comparison == 0) {
                return midpoint;
            }
            if (comparison < 0) {
                low = midpoint;
            } else {
                high = midpoint;
            }
        }
        return low.add(high, context).divide(TWO, context);
    }
}

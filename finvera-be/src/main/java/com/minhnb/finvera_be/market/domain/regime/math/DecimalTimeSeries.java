package com.minhnb.finvera_be.market.domain.regime.math;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Decimal-only time-series operations used by versioned financial rules.
 *
 * <p>The public values are consistently rounded to six decimal places. Intermediate operations use
 * {@link MathContext#DECIMAL128}; no binary floating point is used.
 */
public final class DecimalTimeSeries {

    public static final int RESULT_SCALE = 6;

    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final MathContext CONTEXT = MathContext.DECIMAL128;

    private DecimalTimeSeries() {
    }

    /** Returns the simple return {@code current / previous - 1}. */
    public static BigDecimal simpleReturn(BigDecimal current, BigDecimal previous) {
        requireNonNull(current, "current");
        requireNonNull(previous, "previous");
        if (previous.signum() == 0) {
            throw new IllegalArgumentException("previous must be non-zero");
        }
        return scale(current.divide(previous, CONTEXT).subtract(ONE));
    }

    /** Returns the simple moving average of the most recent {@code period} observations. */
    public static BigDecimal sma(List<BigDecimal> observations, int period) {
        requirePeriod(observations, period);
        BigDecimal sum = BigDecimal.ZERO;
        for (int index = observations.size() - period; index < observations.size(); index++) {
            sum = sum.add(valueAt(observations, index));
        }
        return scale(sum.divide(BigDecimal.valueOf(period), CONTEXT));
    }

    /** Returns the median, averaging the two middle values for an even population. */
    public static BigDecimal median(List<BigDecimal> values) {
        requireValues(values);
        List<BigDecimal> sorted = values.stream()
                .map(value -> requireNonNull(value, "value"))
                .sorted(Comparator.naturalOrder())
                .toList();
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return scale(sorted.get(middle));
        }
        return scale(sorted.get(middle - 1).add(sorted.get(middle)).divide(TWO, CONTEXT));
    }

    /** Returns the population standard deviation, not the sample standard deviation. */
    public static BigDecimal populationStandardDeviation(List<BigDecimal> values) {
        requireValues(values);
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            sum = sum.add(requireNonNull(value, "value"));
        }
        BigDecimal mean = sum.divide(BigDecimal.valueOf(values.size()), CONTEXT);
        BigDecimal squaredDeviationSum = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            BigDecimal deviation = value.subtract(mean);
            squaredDeviationSum = squaredDeviationSum.add(deviation.multiply(deviation, CONTEXT));
        }
        BigDecimal variance = squaredDeviationSum.divide(BigDecimal.valueOf(values.size()), CONTEXT);
        return scale(variance.sqrt(CONTEXT));
    }

    /**
     * Returns an inclusive 0-100 percentile using the mid-rank for tied values.
     *
     * <p>For a population of four values, the two tied values at ranks two and three have percentile
     * 50. The sole member of a population is assigned 100.
     */
    public static BigDecimal percentileMidRank(BigDecimal value, List<BigDecimal> population) {
        requireNonNull(value, "value");
        requireValues(population);
        if (population.size() == 1) {
            return HUNDRED.setScale(RESULT_SCALE);
        }

        int lower = 0;
        int equal = 0;
        for (BigDecimal candidate : population) {
            int comparison = requireNonNull(candidate, "population value").compareTo(value);
            if (comparison < 0) {
                lower++;
            } else if (comparison == 0) {
                equal++;
            }
        }
        if (equal == 0) {
            throw new IllegalArgumentException("value must be present in the population");
        }
        BigDecimal midRank = BigDecimal.valueOf(lower)
                .add(BigDecimal.valueOf(equal + 1L).divide(TWO, CONTEXT));
        return scale(midRank.subtract(ONE)
                .divide(BigDecimal.valueOf(population.size() - 1L), CONTEXT)
                .multiply(HUNDRED, CONTEXT));
    }

    /**
     * Returns the latest Wilder RSI for the supplied closing sequence.
     * A flat sequence is neutral at 50; an all-gain or all-loss sequence returns 100 or 0.
     */
    public static BigDecimal wilderRsi(List<BigDecimal> closes, int period) {
        requirePeriod(closes, period + 1);
        BigDecimal gains = BigDecimal.ZERO;
        BigDecimal losses = BigDecimal.ZERO;
        for (int index = 1; index <= period; index++) {
            BigDecimal change = valueAt(closes, index).subtract(valueAt(closes, index - 1));
            if (change.signum() > 0) {
                gains = gains.add(change);
            } else {
                losses = losses.add(change.abs());
            }
        }
        BigDecimal averageGain = gains.divide(BigDecimal.valueOf(period), CONTEXT);
        BigDecimal averageLoss = losses.divide(BigDecimal.valueOf(period), CONTEXT);
        for (int index = period + 1; index < closes.size(); index++) {
            BigDecimal change = valueAt(closes, index).subtract(valueAt(closes, index - 1));
            BigDecimal gain = change.signum() > 0 ? change : BigDecimal.ZERO;
            BigDecimal loss = change.signum() < 0 ? change.abs() : BigDecimal.ZERO;
            averageGain = averageGain.multiply(BigDecimal.valueOf(period - 1L), CONTEXT)
                    .add(gain).divide(BigDecimal.valueOf(period), CONTEXT);
            averageLoss = averageLoss.multiply(BigDecimal.valueOf(period - 1L), CONTEXT)
                    .add(loss).divide(BigDecimal.valueOf(period), CONTEXT);
        }
        if (averageGain.signum() == 0 && averageLoss.signum() == 0) {
            return scale(BigDecimal.valueOf(50));
        }
        if (averageLoss.signum() == 0) {
            return HUNDRED.setScale(RESULT_SCALE);
        }
        if (averageGain.signum() == 0) {
            return BigDecimal.ZERO.setScale(RESULT_SCALE);
        }
        BigDecimal relativeStrength = averageGain.divide(averageLoss, CONTEXT);
        return scale(HUNDRED.subtract(HUNDRED.divide(ONE.add(relativeStrength), CONTEXT)));
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(RESULT_SCALE, RoundingMode.HALF_UP);
    }

    private static void requirePeriod(List<BigDecimal> values, int period) {
        if (period <= 0) {
            throw new IllegalArgumentException("period must be positive");
        }
        requireValues(values);
        if (values.size() < period) {
            throw new IllegalArgumentException("insufficient observations for period");
        }
    }

    private static void requireValues(List<BigDecimal> values) {
        Objects.requireNonNull(values, "values");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
    }

    private static BigDecimal valueAt(List<BigDecimal> values, int index) {
        return requireNonNull(values.get(index), "value");
    }

    private static BigDecimal requireNonNull(BigDecimal value, String name) {
        return Objects.requireNonNull(value, name);
    }
}

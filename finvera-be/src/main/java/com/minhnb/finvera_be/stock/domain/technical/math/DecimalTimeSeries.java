package com.minhnb.finvera_be.stock.domain.technical.math;

import com.minhnb.finvera_be.stock.domain.model.DecimalMath;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Framework-free decimal time-series math shared by {@code
 * technical-indicators-v1}: simple average, Wilder-smoothed seed-then-recurse
 * (RSI/ATR), EMA seed-then-recurse (MACD), and population standard deviation
 * (Bollinger Bands). Every step is scale-12 {@code HALF_UP}, matching
 * {@code tools/market-data/fixture-gen/generate_stock_technical_fixtures.py}
 * exactly so the golden-vector fixtures are a real independent cross-check.
 */
public final class DecimalTimeSeries {

    private static final BigDecimal ONE = BigDecimal.ONE;

    private DecimalTimeSeries() {
    }

    /** Arithmetic mean at scale 12, {@code HALF_UP}. */
    public static BigDecimal average(List<BigDecimal> values) {
        Objects.requireNonNull(values, "values");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return DecimalMath.divide12(sum, BigDecimal.valueOf(values.size()));
    }

    /**
     * Wilder seed-then-recurse final value: seeds at the simple average of
     * the first {@code period} values, then recurses
     * {@code avg[i] = (avg[i-1] * (period-1) + values[i]) / period} over the
     * remainder, each step rounded at the division. Used for RSI14 avgGain /
     * avgLoss and for ATR14, on an ordered gain/loss/true-range series.
     */
    public static BigDecimal wilderFinal(List<BigDecimal> values, int period) {
        Objects.requireNonNull(values, "values");
        if (period <= 0) {
            throw new IllegalArgumentException("period must be positive");
        }
        if (values.size() < period) {
            throw new IllegalArgumentException("values must contain at least " + period + " entries");
        }
        BigDecimal avg = average(values.subList(0, period));
        BigDecimal periodMinusOne = BigDecimal.valueOf(period - 1L);
        BigDecimal periodValue = BigDecimal.valueOf(period);
        for (int i = period; i < values.size(); i++) {
            avg = DecimalMath.divide12(avg.multiply(periodMinusOne).add(values.get(i)), periodValue);
        }
        return avg;
    }

    /**
     * EMA seed-then-recurse series: seeds at the simple average of the first
     * {@code period} values (aligned to result index 0), then recurses
     * {@code ema[i] = values[i] * k + ema[i-1] * (1-k)} with
     * {@code k = 2 / (period + 1)}, each step explicitly rounded to scale 12
     * (rule U-3) since it is a multiply/add composite, not a division. The
     * returned list's element {@code j} corresponds to input index
     * {@code period - 1 + j}.
     */
    public static List<BigDecimal> emaSeries(List<BigDecimal> values, int period) {
        Objects.requireNonNull(values, "values");
        if (period <= 0) {
            throw new IllegalArgumentException("period must be positive");
        }
        if (values.size() < period) {
            throw new IllegalArgumentException("values must contain at least " + period + " entries");
        }
        BigDecimal k = DecimalMath.divide12(BigDecimal.valueOf(2), BigDecimal.valueOf(period + 1L));
        BigDecimal oneMinusK = ONE.subtract(k);
        List<BigDecimal> series = new ArrayList<>();
        BigDecimal ema = average(values.subList(0, period));
        series.add(ema);
        for (int i = period; i < values.size(); i++) {
            ema = DecimalMath.scale12(values.get(i).multiply(k).add(ema.multiply(oneMinusK)));
            series.add(ema);
        }
        return series;
    }

    /** Population standard deviation at scale 12, {@code HALF_UP} (BBANDS divisor is the count, not count-1). */
    public static BigDecimal populationStandardDeviation(List<BigDecimal> values, BigDecimal mean) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(mean, "mean");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
        BigDecimal sumSquares = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            BigDecimal diff = value.subtract(mean);
            sumSquares = sumSquares.add(diff.multiply(diff));
        }
        BigDecimal variance = DecimalMath.divide12(sumSquares, BigDecimal.valueOf(values.size()));
        return DecimalMath.sqrt12(variance);
    }
}

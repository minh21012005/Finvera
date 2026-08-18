package com.minhnb.finvera_be.stock.domain.technical;

import com.minhnb.finvera_be.stock.domain.model.DecimalMath;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.IndicatorCode;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.IndicatorComponent;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.MetricApplicability;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.Unit;
import com.minhnb.finvera_be.stock.domain.technical.math.DecimalTimeSeries;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * {@code contracts/technical-indicators-v1.md}, the single normative
 * authority for these formulas. This engine is a pure function of an
 * already-resolved close/high/low/volume series: rule U-2 (adjusted vs. raw
 * price basis selection) is the caller's responsibility, since it depends on
 * the source series' {@code adjustment_status}, which this engine does not
 * see.
 *
 * <p>Applicability rule: for a single-{@code VALUE}-component indicator
 * (MA20/MA50/MA200, RSI14, AVG_VOLUME20, RELATIVE_VOLUME), the indicator-level
 * {@code applicability} mirrors its one component's applicability — a
 * {@code NOT_APPLICABLE} value (only reachable for RELATIVE_VOLUME, a
 * suspended-symbol zero-denominator case) is not hidden behind a nominally
 * "DEFINED" indicator with a null value. For a multi-component indicator
 * (MACD, BBANDS, ATR14), the indicator stays {@code DEFINED} whenever its bar
 * count is met, even if one sub-component (BANDWIDTH, PERCENT_OF_CLOSE) is
 * individually {@code NOT_APPLICABLE} — the other components remain genuinely
 * defined and are not withheld for a sibling's degenerate input.
 */
public final class TechnicalIndicatorsV1 {

    public static final String RULE_VERSION = "technical-indicators-v1";

    private static final int FIXED_WINDOW = 250;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final String INSUFFICIENT_HISTORY = "INSUFFICIENT_HISTORY";
    private static final String NOT_APPLICABLE_REASON = "NOT_APPLICABLE";

    public IndicatorSetResult compute(List<TechnicalBar> ascendingBars) {
        Objects.requireNonNull(ascendingBars, "ascendingBars");
        List<TechnicalBar> bars = List.copyOf(ascendingBars);
        List<IndicatorResult> results = new ArrayList<>();
        results.add(simpleMovingAverage(bars, IndicatorCode.MA20, 20));
        results.add(simpleMovingAverage(bars, IndicatorCode.MA50, 50));
        results.add(simpleMovingAverage(bars, IndicatorCode.MA200, 200));
        results.add(rsi14(bars));
        results.add(macd(bars));
        results.add(bbands(bars));
        results.add(atr14(bars));
        results.add(avgVolume20(bars));
        results.add(relativeVolume(bars));
        return new IndicatorSetResult(results);
    }

    private IndicatorResult simpleMovingAverage(List<TechnicalBar> bars, IndicatorCode code, int period) {
        if (bars.size() < period) {
            return insufficientHistory(code, period, bars.size());
        }
        List<TechnicalBar> window = bars.subList(bars.size() - period, bars.size());
        BigDecimal value = DecimalTimeSeries.average(closes(window));
        ComponentResult component = new ComponentResult(IndicatorComponent.VALUE, value, Unit.VND, 2,
                MetricApplicability.DEFINED, null);
        return available(code, period, window, List.of(component), MetricApplicability.DEFINED, null,
                TechnicalInputHashes.ofCloses(window));
    }

    private IndicatorResult rsi14(List<TechnicalBar> bars) {
        if (bars.size() < FIXED_WINDOW) {
            return insufficientHistory(IndicatorCode.RSI14, FIXED_WINDOW, bars.size());
        }
        List<TechnicalBar> window = bars.subList(bars.size() - FIXED_WINDOW, bars.size());
        List<BigDecimal> gains = new ArrayList<>();
        List<BigDecimal> losses = new ArrayList<>();
        for (int i = 1; i < window.size(); i++) {
            BigDecimal delta = window.get(i).close().subtract(window.get(i - 1).close());
            gains.add(delta.signum() > 0 ? delta : BigDecimal.ZERO);
            losses.add(delta.signum() < 0 ? delta.negate() : BigDecimal.ZERO);
        }
        BigDecimal avgGain = DecimalTimeSeries.wilderFinal(gains, 14);
        BigDecimal avgLoss = DecimalTimeSeries.wilderFinal(losses, 14);
        BigDecimal rsi;
        if (avgLoss.signum() == 0 && avgGain.signum() == 0) {
            rsi = new BigDecimal("50").setScale(DecimalMath.INTERMEDIATE_SCALE);
        } else if (avgLoss.signum() == 0) {
            rsi = new BigDecimal("100").setScale(DecimalMath.INTERMEDIATE_SCALE);
        } else {
            BigDecimal rs = DecimalMath.divide12(avgGain, avgLoss);
            rsi = ONE_HUNDRED.subtract(DecimalMath.divide12(ONE_HUNDRED, BigDecimal.ONE.add(rs)));
        }
        ComponentResult component = new ComponentResult(IndicatorComponent.VALUE, rsi, Unit.POINTS, 2,
                MetricApplicability.DEFINED, null);
        return available(IndicatorCode.RSI14, FIXED_WINDOW, window, List.of(component), MetricApplicability.DEFINED,
                null, TechnicalInputHashes.ofCloses(window));
    }

    private IndicatorResult macd(List<TechnicalBar> bars) {
        if (bars.size() < FIXED_WINDOW) {
            return insufficientHistory(IndicatorCode.MACD, FIXED_WINDOW, bars.size());
        }
        List<TechnicalBar> window = bars.subList(bars.size() - FIXED_WINDOW, bars.size());
        List<BigDecimal> closes = closes(window);
        int fastPeriod = 12;
        int slowPeriod = 26;
        int fastOffset = fastPeriod - 1;
        int slowOffset = slowPeriod - 1;
        List<BigDecimal> emaFast = DecimalTimeSeries.emaSeries(closes, fastPeriod);
        List<BigDecimal> emaSlow = DecimalTimeSeries.emaSeries(closes, slowPeriod);

        List<BigDecimal> macdLine = new ArrayList<>();
        for (int j = slowOffset; j < closes.size(); j++) {
            BigDecimal fast = emaFast.get(j - fastOffset);
            BigDecimal slow = emaSlow.get(j - slowOffset);
            macdLine.add(DecimalMath.scale12(fast.subtract(slow)));
        }

        int signalPeriod = 9;
        BigDecimal signal = DecimalTimeSeries.average(macdLine.subList(0, signalPeriod));
        BigDecimal k = DecimalMath.divide12(BigDecimal.valueOf(2), BigDecimal.valueOf(signalPeriod + 1L));
        BigDecimal oneMinusK = BigDecimal.ONE.subtract(k);
        for (int i = signalPeriod; i < macdLine.size(); i++) {
            signal = DecimalMath.scale12(macdLine.get(i).multiply(k).add(signal.multiply(oneMinusK)));
        }

        BigDecimal macdLineFinal = macdLine.getLast();
        BigDecimal histogram = DecimalMath.scale12(macdLineFinal.subtract(signal));

        List<ComponentResult> components = List.of(
                new ComponentResult(IndicatorComponent.MACD_LINE, macdLineFinal, Unit.VND, 2,
                        MetricApplicability.DEFINED, null),
                new ComponentResult(IndicatorComponent.SIGNAL, signal, Unit.VND, 2, MetricApplicability.DEFINED,
                        null),
                new ComponentResult(IndicatorComponent.HISTOGRAM, histogram, Unit.VND, 2, MetricApplicability.DEFINED,
                        null));
        return available(IndicatorCode.MACD, FIXED_WINDOW, window, components, MetricApplicability.DEFINED, null,
                TechnicalInputHashes.ofCloses(window));
    }

    private IndicatorResult bbands(List<TechnicalBar> bars) {
        int period = 20;
        if (bars.size() < period) {
            return insufficientHistory(IndicatorCode.BBANDS, period, bars.size());
        }
        List<TechnicalBar> window = bars.subList(bars.size() - period, bars.size());
        List<BigDecimal> closes = closes(window);
        BigDecimal middle = DecimalTimeSeries.average(closes);
        BigDecimal sigma = DecimalTimeSeries.populationStandardDeviation(closes, middle);
        BigDecimal upper = DecimalMath.scale12(middle.add(BigDecimal.valueOf(2).multiply(sigma)));
        BigDecimal lower = DecimalMath.scale12(middle.subtract(BigDecimal.valueOf(2).multiply(sigma)));

        ComponentResult bandwidthComponent;
        BigDecimal bandwidthRatio = DecimalMath.divide12(upper.subtract(lower), middle);
        if (bandwidthRatio == null) {
            bandwidthComponent = new ComponentResult(IndicatorComponent.BANDWIDTH, null, Unit.PERCENT, 2,
                    MetricApplicability.NOT_APPLICABLE, NOT_APPLICABLE_REASON);
        } else {
            BigDecimal bandwidth = DecimalMath.scale12(bandwidthRatio.multiply(ONE_HUNDRED));
            bandwidthComponent = new ComponentResult(IndicatorComponent.BANDWIDTH, bandwidth, Unit.PERCENT, 2,
                    MetricApplicability.DEFINED, null);
        }

        List<ComponentResult> components = List.of(
                new ComponentResult(IndicatorComponent.UPPER, upper, Unit.VND, 2, MetricApplicability.DEFINED, null),
                new ComponentResult(IndicatorComponent.MIDDLE, middle, Unit.VND, 2, MetricApplicability.DEFINED,
                        null),
                new ComponentResult(IndicatorComponent.LOWER, lower, Unit.VND, 2, MetricApplicability.DEFINED, null),
                bandwidthComponent);
        return available(IndicatorCode.BBANDS, period, window, components, MetricApplicability.DEFINED, null,
                TechnicalInputHashes.ofCloses(window));
    }

    private IndicatorResult atr14(List<TechnicalBar> bars) {
        if (bars.size() < FIXED_WINDOW) {
            return insufficientHistory(IndicatorCode.ATR14, FIXED_WINDOW, bars.size());
        }
        List<TechnicalBar> window = bars.subList(bars.size() - FIXED_WINDOW, bars.size());
        List<BigDecimal> trueRanges = new ArrayList<>();
        for (int i = 1; i < window.size(); i++) {
            TechnicalBar bar = window.get(i);
            BigDecimal previousClose = window.get(i - 1).close();
            BigDecimal a = bar.high().subtract(bar.low());
            BigDecimal b = bar.high().subtract(previousClose).abs();
            BigDecimal c = bar.low().subtract(previousClose).abs();
            trueRanges.add(a.max(b).max(c));
        }
        BigDecimal atr = DecimalTimeSeries.wilderFinal(trueRanges, 14);
        BigDecimal lastClose = window.getLast().close();

        ComponentResult percentComponent;
        BigDecimal percentRatio = DecimalMath.divide12(atr, lastClose);
        if (percentRatio == null) {
            percentComponent = new ComponentResult(IndicatorComponent.PERCENT_OF_CLOSE, null, Unit.PERCENT, 2,
                    MetricApplicability.NOT_APPLICABLE, NOT_APPLICABLE_REASON);
        } else {
            BigDecimal percent = DecimalMath.scale12(percentRatio.multiply(ONE_HUNDRED));
            percentComponent = new ComponentResult(IndicatorComponent.PERCENT_OF_CLOSE, percent, Unit.PERCENT, 2,
                    MetricApplicability.DEFINED, null);
        }

        List<ComponentResult> components = List.of(
                new ComponentResult(IndicatorComponent.VALUE, atr, Unit.VND, 2, MetricApplicability.DEFINED, null),
                percentComponent);
        return available(IndicatorCode.ATR14, FIXED_WINDOW, window, components, MetricApplicability.DEFINED, null,
                TechnicalInputHashes.ofHighLowClose(window));
    }

    private IndicatorResult avgVolume20(List<TechnicalBar> bars) {
        int period = 20;
        if (bars.size() < period) {
            return insufficientHistory(IndicatorCode.AVG_VOLUME20, period, bars.size());
        }
        List<TechnicalBar> window = bars.subList(bars.size() - period, bars.size());
        BigDecimal value = DecimalTimeSeries.average(volumes(window));
        ComponentResult component = new ComponentResult(IndicatorComponent.VALUE, value, Unit.SHARES, 0,
                MetricApplicability.DEFINED, null);
        return available(IndicatorCode.AVG_VOLUME20, period, window, List.of(component), MetricApplicability.DEFINED,
                null, TechnicalInputHashes.ofVolumes(window));
    }

    private IndicatorResult relativeVolume(List<TechnicalBar> bars) {
        int required = 21;
        if (bars.size() < required) {
            return insufficientHistory(IndicatorCode.RELATIVE_VOLUME, required, bars.size());
        }
        List<TechnicalBar> window = bars.subList(bars.size() - required, bars.size());
        BigDecimal priorAverage = DecimalTimeSeries.average(volumes(window.subList(0, 20)));
        BigDecimal currentVolume = BigDecimal.valueOf(window.getLast().volume());
        BigDecimal ratio = DecimalMath.divide12(currentVolume, priorAverage);

        MetricApplicability applicability = ratio == null ? MetricApplicability.NOT_APPLICABLE
                : MetricApplicability.DEFINED;
        String reasonCode = ratio == null ? NOT_APPLICABLE_REASON : null;
        ComponentResult component = new ComponentResult(IndicatorComponent.VALUE, ratio, Unit.RATIO, 2,
                applicability, reasonCode);
        return available(IndicatorCode.RELATIVE_VOLUME, required, window, List.of(component), applicability,
                reasonCode, TechnicalInputHashes.ofVolumes(window));
    }

    private static List<BigDecimal> closes(List<TechnicalBar> bars) {
        return bars.stream().map(TechnicalBar::close).toList();
    }

    private static List<BigDecimal> volumes(List<TechnicalBar> bars) {
        return bars.stream().map(bar -> BigDecimal.valueOf(bar.volume())).toList();
    }

    private static IndicatorResult insufficientHistory(IndicatorCode code, int required, int available) {
        return new IndicatorResult(code, MetricApplicability.MISSING, required, available, null, null, 0, null,
                INSUFFICIENT_HISTORY, List.of());
    }

    private static IndicatorResult available(IndicatorCode code, int required, List<TechnicalBar> window,
            List<ComponentResult> components, MetricApplicability applicability, String reasonCode,
            String inputSetHash) {
        return new IndicatorResult(code, applicability, required, window.size(), window.getFirst().tradingDate(),
                window.getLast().tradingDate(), window.size(), inputSetHash, reasonCode, components);
    }

    public record TechnicalBar(LocalDate tradingDate, BigDecimal close, BigDecimal high, BigDecimal low,
            long volume) {
    }

    public record ComponentResult(IndicatorComponent componentCode, BigDecimal value, Unit unit,
            int displayPrecision, MetricApplicability applicability, String reasonCode) {
    }

    public record IndicatorResult(IndicatorCode indicatorCode, MetricApplicability applicability, int requiredBars,
            int availableBars, LocalDate windowStartDate, LocalDate windowEndDate, int inputBarCount,
            String inputSetHash, String reasonCode, List<ComponentResult> components) {
        public IndicatorResult {
            components = List.copyOf(components);
        }
    }

    public record IndicatorSetResult(List<IndicatorResult> indicators) {
        public IndicatorSetResult {
            indicators = List.copyOf(indicators);
        }
    }
}

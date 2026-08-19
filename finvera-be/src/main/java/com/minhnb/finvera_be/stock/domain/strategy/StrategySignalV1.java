package com.minhnb.finvera_be.stock.domain.strategy;

import com.minhnb.finvera_be.stock.domain.model.DecimalMath;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.Direction;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.IndicatorCode;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.IndicatorComponent;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.MetricApplicability;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.RiskFactorCode;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.RiskLevel;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.SignalStrength;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.StrategyCode;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.BreakoutCondition;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.BreakoutResult;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.CandidateFacts;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.DailyBarPoint;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.IndicatorSnapshot;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.TrendDirection;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.TrendResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * {@code contracts/strategy-signal-v1.md}, the single normative authority for
 * every strategy's entry condition, level formula, and risk factor. Pure and
 * framework-free, matching {@code TechnicalIndicatorsV1}/{@code ScreenerV1}.
 *
 * <p>U-1: every strategy/level/factor reads only already-accepted values
 * supplied by the caller in {@link StrategyInputs}/{@link RiskFactorInputs};
 * this class performs no I/O and recomputes nothing {@code
 * technical-indicators-v1}/{@code screener-v1} already computed — Trend/
 * Breakout are derived by calling {@link ScreenerV1#deriveTrend} and
 * {@link ScreenerV1#deriveBreakout} directly (FR-012).
 */
public final class StrategySignalV1 {

    public static final String RULE_VERSION = "strategy-signal-v1";

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal ENTRY_HALF_WIDTH_ATR_MULT = new BigDecimal("0.25");
    private static final BigDecimal STOP_ATR_MULT = new BigDecimal("2");
    private static final BigDecimal TARGET1_ATR_MULT = new BigDecimal("4");
    private static final BigDecimal TARGET2_ATR_MULT = new BigDecimal("6");
    private static final BigDecimal RISK_REWARD = new BigDecimal("2.0000");
    private static final int LEVEL_DISPLAY_SCALE = 6;
    private static final String INSUFFICIENT_HISTORY = "INSUFFICIENT_HISTORY";
    private static final String INVALID_LEVELS = "INVALID_LEVELS";
    private static final String INSUFFICIENT_RISK_FACTORS = "INSUFFICIENT_RISK_FACTORS";
    private static final int MIN_AVAILABLE_RISK_FACTORS = 4;

    private StrategySignalV1() {
    }

    // ── Entry evaluation ─────────────────────────────────────────────────────

    public static EntryEvaluation evaluate(StrategyCode strategyCode, StrategyInputs inputs) {
        Objects.requireNonNull(strategyCode, "strategyCode");
        Objects.requireNonNull(inputs, "inputs");

        Availability availability = requiredAvailability(strategyCode, inputs);
        if (availability.status() == AvailabilityStatus.INSUFFICIENT) {
            return EntryEvaluation.insufficientHistory(strategyCode);
        }
        if (availability.status() == AvailabilityStatus.WITHHELD) {
            return EntryEvaluation.withheld(strategyCode, availability.reasonCode());
        }

        Map<String, String> evidence = new LinkedHashMap<>();
        boolean triggered = switch (strategyCode) {
            case TREND_FOLLOWING -> trendFollowing(inputs, evidence);
            case MOMENTUM -> momentum(inputs, evidence);
            case BREAKOUT -> breakout(inputs, evidence);
            case PULLBACK -> pullback(inputs, evidence);
            case MEAN_REVERSION -> meanReversion(inputs, evidence);
            case MA_CROSSOVER -> maCrossover(inputs, evidence);
            case MACD_BASED -> macdBased(inputs, evidence);
            case RSI_BASED -> rsiBased(inputs, evidence);
        };
        if (!triggered) {
            return EntryEvaluation.noSignal(strategyCode);
        }

        BigDecimal close = inputs.close();
        BigDecimal atr14 = componentValue(inputs.current(), IndicatorCode.ATR14, IndicatorComponent.VALUE);
        if (atr14 == null || atr14.signum() <= 0) {
            // Also reached when a strategy whose own minimum bar count (e.g.
            // Breakout's 21) is lower than ATR14's fixed 250-session window
            // triggers before ATR14 itself is available — the shared level
            // framework still cannot produce a level, so it is withheld the
            // same as a degenerate zero/negative ATR14.
            return EntryEvaluation.withheld(strategyCode, INVALID_LEVELS);
        }

        LevelSet levels = computeLevels(close, atr14);
        return EntryEvaluation.signal(strategyCode, levels, Map.copyOf(evidence));
    }

    // ── Strategy conditions (contracts/strategy-signal-v1.md) ───────────────

    private static boolean trendFollowing(StrategyInputs in, Map<String, String> evidence) {
        TrendResult trend = ScreenerV1.deriveTrend(screenerFacts(in));
        BigDecimal ma20 = componentValue(in.current(), IndicatorCode.MA20, IndicatorComponent.VALUE);
        boolean matched = !trend.unavailable() && trend.direction() == TrendDirection.UPTREND
                && in.close().compareTo(ma20) > 0;
        evidence.put("trend", trend.unavailable() ? "UNAVAILABLE" : trend.direction().name());
        evidence.put("close", in.close().toPlainString());
        evidence.put("ma20", ma20.toPlainString());
        return matched;
    }

    private static boolean momentum(StrategyInputs in, Map<String, String> evidence) {
        BigDecimal rsi14 = componentValue(in.current(), IndicatorCode.RSI14, IndicatorComponent.VALUE);
        BigDecimal histogram = componentValue(in.current(), IndicatorCode.MACD, IndicatorComponent.HISTOGRAM);
        evidence.put("rsi14", rsi14.toPlainString());
        evidence.put("macdHistogram", histogram.toPlainString());
        return rsi14.compareTo(new BigDecimal("60")) >= 0 && histogram.signum() > 0;
    }

    private static boolean breakout(StrategyInputs in, Map<String, String> evidence) {
        BreakoutResult breakoutResult = ScreenerV1.deriveBreakout(screenerFacts(in));
        BigDecimal relativeVolume = componentValue(in.current(), IndicatorCode.RELATIVE_VOLUME, IndicatorComponent.VALUE);
        evidence.put("breakout", breakoutResult.unavailable() ? "UNAVAILABLE" : breakoutResult.condition().name());
        evidence.put("relativeVolume", relativeVolume.toPlainString());
        return !breakoutResult.unavailable() && breakoutResult.condition() == BreakoutCondition.BREAKOUT_UP
                && relativeVolume.compareTo(new BigDecimal("1.5")) >= 0;
    }

    private static boolean pullback(StrategyInputs in, Map<String, String> evidence) {
        BigDecimal ma20 = componentValue(in.current(), IndicatorCode.MA20, IndicatorComponent.VALUE);
        BigDecimal ma50 = componentValue(in.current(), IndicatorCode.MA50, IndicatorComponent.VALUE);
        BigDecimal ma200 = componentValue(in.current(), IndicatorCode.MA200, IndicatorComponent.VALUE);
        BigDecimal rsi14 = componentValue(in.current(), IndicatorCode.RSI14, IndicatorComponent.VALUE);
        evidence.put("ma20", ma20.toPlainString());
        evidence.put("ma50", ma50.toPlainString());
        evidence.put("ma200", ma200.toPlainString());
        evidence.put("rsi14", rsi14.toPlainString());
        evidence.put("close", in.close().toPlainString());
        return ma20.compareTo(ma50) > 0 && ma50.compareTo(ma200) > 0
                && rsi14.compareTo(new BigDecimal("40")) >= 0 && rsi14.compareTo(new BigDecimal("55")) <= 0
                && in.close().compareTo(ma50) > 0;
    }

    private static boolean meanReversion(StrategyInputs in, Map<String, String> evidence) {
        BigDecimal rsi14 = componentValue(in.current(), IndicatorCode.RSI14, IndicatorComponent.VALUE);
        BigDecimal lower = componentValue(in.current(), IndicatorCode.BBANDS, IndicatorComponent.LOWER);
        evidence.put("rsi14", rsi14.toPlainString());
        evidence.put("bbandsLower", lower.toPlainString());
        evidence.put("close", in.close().toPlainString());
        return rsi14.compareTo(new BigDecimal("30")) <= 0 && in.close().compareTo(lower) < 0;
    }

    private static boolean maCrossover(StrategyInputs in, Map<String, String> evidence) {
        BigDecimal ma20Today = componentValue(in.current(), IndicatorCode.MA20, IndicatorComponent.VALUE);
        BigDecimal ma50Today = componentValue(in.current(), IndicatorCode.MA50, IndicatorComponent.VALUE);
        BigDecimal ma20Yesterday = componentValue(in.prior(), IndicatorCode.MA20, IndicatorComponent.VALUE);
        BigDecimal ma50Yesterday = componentValue(in.prior(), IndicatorCode.MA50, IndicatorComponent.VALUE);
        evidence.put("ma20", ma20Today.toPlainString());
        evidence.put("ma50", ma50Today.toPlainString());
        evidence.put("ma20Prior", ma20Yesterday.toPlainString());
        evidence.put("ma50Prior", ma50Yesterday.toPlainString());
        return ma20Yesterday.compareTo(ma50Yesterday) <= 0 && ma20Today.compareTo(ma50Today) > 0;
    }

    private static boolean macdBased(StrategyInputs in, Map<String, String> evidence) {
        BigDecimal histogramToday = componentValue(in.current(), IndicatorCode.MACD, IndicatorComponent.HISTOGRAM);
        BigDecimal histogramYesterday = componentValue(in.prior(), IndicatorCode.MACD, IndicatorComponent.HISTOGRAM);
        evidence.put("macdHistogram", histogramToday.toPlainString());
        evidence.put("macdHistogramPrior", histogramYesterday.toPlainString());
        return histogramYesterday.compareTo(BigDecimal.ZERO) <= 0 && histogramToday.signum() > 0;
    }

    private static boolean rsiBased(StrategyInputs in, Map<String, String> evidence) {
        BigDecimal rsiToday = componentValue(in.current(), IndicatorCode.RSI14, IndicatorComponent.VALUE);
        BigDecimal rsiYesterday = componentValue(in.prior(), IndicatorCode.RSI14, IndicatorComponent.VALUE);
        evidence.put("rsi14", rsiToday.toPlainString());
        evidence.put("rsi14Prior", rsiYesterday.toPlainString());
        return rsiYesterday.compareTo(new BigDecimal("30")) <= 0 && rsiToday.compareTo(new BigDecimal("30")) > 0;
    }

    // ── Availability (U-5 insufficiency / DATA-003 withholding) ─────────────

    private static Availability requiredAvailability(StrategyCode strategyCode, StrategyInputs in) {
        Availability closeAvailability = in.close() == null ? Availability.insufficient() : Availability.ok();
        Availability result = closeAvailability;
        for (IndicatorCode code : currentIndicatorsFor(strategyCode)) {
            result = worst(result, availability(in.current().get(code)));
        }
        for (IndicatorCode code : priorIndicatorsFor(strategyCode)) {
            result = worst(result, availability(in.prior().get(code)));
        }
        if (strategyCode == StrategyCode.TREND_FOLLOWING) {
            TrendResult trend = ScreenerV1.deriveTrend(screenerFacts(in));
            if (trend.unavailable()) {
                result = worst(result, Availability.insufficient());
            }
        }
        if (strategyCode == StrategyCode.BREAKOUT) {
            BreakoutResult breakoutResult = ScreenerV1.deriveBreakout(screenerFacts(in));
            if (breakoutResult.unavailable()) {
                result = worst(result, Availability.insufficient());
            }
        }
        return result;
    }

    /** Exposed for the service layer's per-strategy input-linkage/idempotency hash (T009). */
    public static List<IndicatorCode> currentIndicatorsFor(StrategyCode strategyCode) {
        return switch (strategyCode) {
            case TREND_FOLLOWING -> List.of(IndicatorCode.MA20, IndicatorCode.MA50, IndicatorCode.MA200);
            case MOMENTUM -> List.of(IndicatorCode.RSI14, IndicatorCode.MACD);
            case BREAKOUT -> List.of(IndicatorCode.RELATIVE_VOLUME);
            case PULLBACK -> List.of(IndicatorCode.MA20, IndicatorCode.MA50, IndicatorCode.MA200, IndicatorCode.RSI14);
            case MEAN_REVERSION -> List.of(IndicatorCode.RSI14, IndicatorCode.BBANDS);
            case MA_CROSSOVER -> List.of(IndicatorCode.MA20, IndicatorCode.MA50);
            case MACD_BASED -> List.of(IndicatorCode.MACD);
            case RSI_BASED -> List.of(IndicatorCode.RSI14);
        };
    }

    public static List<IndicatorCode> priorIndicatorsFor(StrategyCode strategyCode) {
        return switch (strategyCode) {
            case MA_CROSSOVER -> List.of(IndicatorCode.MA20, IndicatorCode.MA50);
            case MACD_BASED -> List.of(IndicatorCode.MACD);
            case RSI_BASED -> List.of(IndicatorCode.RSI14);
            default -> List.of();
        };
    }

    private static Availability availability(IndicatorSnapshot snapshot) {
        if (snapshot == null || snapshot.applicability() != MetricApplicability.DEFINED) {
            String reason = snapshot == null ? null : snapshot.qualityReason();
            if (reason == null || INSUFFICIENT_HISTORY.equals(reason)) {
                return Availability.insufficient();
            }
            return Availability.withheld(reason);
        }
        return Availability.ok();
    }

    private static Availability worst(Availability left, Availability right) {
        return right.status().severity() > left.status().severity() ? right : left;
    }

    // ── Level formulas (contracts/strategy-signal-v1.md "Signal levels") ────

    private static LevelSet computeLevels(BigDecimal close, BigDecimal atr14) {
        BigDecimal entryLow = display(close.subtract(ENTRY_HALF_WIDTH_ATR_MULT.multiply(atr14)));
        BigDecimal entryHigh = display(close.add(ENTRY_HALF_WIDTH_ATR_MULT.multiply(atr14)));
        BigDecimal stopLoss = display(close.subtract(STOP_ATR_MULT.multiply(atr14)));
        BigDecimal target1 = display(close.add(TARGET1_ATR_MULT.multiply(atr14)));
        BigDecimal target2 = display(close.add(TARGET2_ATR_MULT.multiply(atr14)));
        return new LevelSet(entryLow, entryHigh, stopLoss, target1, target2, RISK_REWARD);
    }

    private static BigDecimal display(BigDecimal value) {
        return DecimalMath.roundForDisplay(DecimalMath.scale12(value), LEVEL_DISPLAY_SCALE);
    }

    // ── Risk factors and score (contracts/strategy-signal-v1.md) ────────────

    public static RiskAssessment computeRisk(BigDecimal close, LevelSet levels, RiskFactorInputs inputs) {
        Objects.requireNonNull(close, "close");
        Objects.requireNonNull(levels, "levels");
        Objects.requireNonNull(inputs, "inputs");

        List<RiskFactorResult> factors = new ArrayList<>();
        factors.add(volatilityFactor(inputs.volatilityPercentOfClose()));
        factors.add(atrFactor(inputs.atr14Value(), inputs.trailingAverageAtr14Value()));
        factors.add(drawdownFactor(close, inputs.highestCloseTrailing250()));
        factors.add(liquidityFactor(inputs.relativeVolume()));
        factors.add(stopDistanceFactor(close, levels.stopLoss()));
        factors.add(marketRegimeFactor(inputs.regimeScore()));

        List<Integer> availableScores = factors.stream()
                .filter(f -> f.applicability() == MetricApplicability.DEFINED)
                .map(RiskFactorResult::factorScore)
                .toList();

        if (availableScores.size() < MIN_AVAILABLE_RISK_FACTORS) {
            return new RiskAssessment(null, null, null, List.copyOf(factors), List.of(INSUFFICIENT_RISK_FACTORS));
        }

        BigDecimal sum = availableScores.stream().reduce(BigDecimal.ZERO,
                (acc, s) -> acc.add(BigDecimal.valueOf(s)), BigDecimal::add);
        int overallScore = DecimalMath.divide12(sum, BigDecimal.valueOf(availableScores.size()))
                .setScale(0, RoundingMode.HALF_UP).intValueExact();
        RiskLevel riskLevel = riskLevelFor(overallScore);
        SignalStrength strength = strengthFor(riskLevel);
        return new RiskAssessment(overallScore, riskLevel, strength, List.copyOf(factors), List.of());
    }

    private static RiskFactorResult volatilityFactor(RiskFactorInputs.MetricPoint point) {
        return scoredFactor(RiskFactorCode.VOLATILITY, point,
                v -> clampLinearScore(v, new BigDecimal("2"), new BigDecimal("10")));
    }

    private static RiskFactorResult atrFactor(RiskFactorInputs.MetricPoint atr14,
            RiskFactorInputs.MetricPoint trailingAverage) {
        if (!atr14.available() || !trailingAverage.available()) {
            String reason = !atr14.available() ? atr14.reasonCode() : trailingAverage.reasonCode();
            return unavailableFactor(RiskFactorCode.ATR, reason);
        }
        BigDecimal ratio = DecimalMath.divide12(atr14.value(), trailingAverage.value());
        if (ratio == null) {
            return unavailableFactor(RiskFactorCode.ATR, "TRAILING_AVERAGE_ATR_ZERO");
        }
        int score = clampLinearScore(ratio, new BigDecimal("0.75"), new BigDecimal("1.5"));
        return new RiskFactorResult(RiskFactorCode.ATR, DecimalMath.roundForDisplay(ratio, 6), score,
                MetricApplicability.DEFINED, null);
    }

    private static RiskFactorResult drawdownFactor(BigDecimal close, RiskFactorInputs.MetricPoint highestClose) {
        if (!highestClose.available()) {
            return unavailableFactor(RiskFactorCode.DRAWDOWN, highestClose.reasonCode());
        }
        BigDecimal high = highestClose.value();
        if (high.signum() <= 0) {
            return unavailableFactor(RiskFactorCode.DRAWDOWN, "HIGHEST_CLOSE_INVALID");
        }
        BigDecimal declinePercent = DecimalMath.divide12(high.subtract(close), high).multiply(ONE_HUNDRED);
        if (declinePercent.signum() < 0) {
            declinePercent = BigDecimal.ZERO;
        }
        int score = clampLinearScore(declinePercent, BigDecimal.ZERO, new BigDecimal("30"));
        return new RiskFactorResult(RiskFactorCode.DRAWDOWN, DecimalMath.roundForDisplay(declinePercent, 6), score,
                MetricApplicability.DEFINED, null);
    }

    private static RiskFactorResult liquidityFactor(RiskFactorInputs.MetricPoint relativeVolume) {
        return scoredFactor(RiskFactorCode.LIQUIDITY, relativeVolume,
                v -> clampLinearScore(v, new BigDecimal("1.5"), new BigDecimal("0.5")));
    }

    private static RiskFactorResult stopDistanceFactor(BigDecimal close, BigDecimal stopLoss) {
        BigDecimal percent = DecimalMath.divide12(close.subtract(stopLoss), close).multiply(ONE_HUNDRED);
        int score = clampLinearScore(percent, new BigDecimal("3"), new BigDecimal("15"));
        return new RiskFactorResult(RiskFactorCode.STOP_DISTANCE, DecimalMath.roundForDisplay(percent, 6), score,
                MetricApplicability.DEFINED, null);
    }

    private static RiskFactorResult marketRegimeFactor(RiskFactorInputs.MetricPoint regimeScore) {
        if (!regimeScore.available()) {
            return unavailableFactor(RiskFactorCode.MARKET_REGIME, regimeScore.reasonCode());
        }
        int score = ONE_HUNDRED.intValue() - regimeScore.value().setScale(0, RoundingMode.HALF_UP).intValueExact();
        score = Math.max(0, Math.min(100, score));
        return new RiskFactorResult(RiskFactorCode.MARKET_REGIME, regimeScore.value(), score,
                MetricApplicability.DEFINED, null);
    }

    private static RiskFactorResult scoredFactor(RiskFactorCode code, RiskFactorInputs.MetricPoint point,
            java.util.function.Function<BigDecimal, Integer> scorer) {
        if (!point.available()) {
            return unavailableFactor(code, point.reasonCode());
        }
        int score = scorer.apply(point.value());
        return new RiskFactorResult(code, DecimalMath.roundForDisplay(point.value(), 6), score,
                MetricApplicability.DEFINED, null);
    }

    private static RiskFactorResult unavailableFactor(RiskFactorCode code, String reasonCode) {
        return new RiskFactorResult(code, null, null, MetricApplicability.MISSING,
                reasonCode == null ? "INPUT_UNAVAILABLE" : reasonCode);
    }

    private static int clampLinearScore(BigDecimal value, BigDecimal zeroAt, BigDecimal hundredAt) {
        BigDecimal range = hundredAt.subtract(zeroAt);
        BigDecimal ratio = DecimalMath.divide12(value.subtract(zeroAt), range);
        BigDecimal percent = ratio.multiply(ONE_HUNDRED);
        if (percent.signum() < 0) {
            percent = BigDecimal.ZERO;
        } else if (percent.compareTo(ONE_HUNDRED) > 0) {
            percent = ONE_HUNDRED;
        }
        return percent.setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    private static RiskLevel riskLevelFor(int overallScore) {
        if (overallScore <= 33) {
            return RiskLevel.LOW;
        }
        if (overallScore <= 66) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.HIGH;
    }

    private static SignalStrength strengthFor(RiskLevel riskLevel) {
        return switch (riskLevel) {
            case LOW -> SignalStrength.STRONG;
            case MEDIUM -> SignalStrength.MODERATE;
            case HIGH -> SignalStrength.WEAK;
        };
    }

    // ── Shared helpers ───────────────────────────────────────────────────────

    private static BigDecimal componentValue(Map<IndicatorCode, IndicatorSnapshot> snapshots, IndicatorCode code,
            IndicatorComponent component) {
        IndicatorSnapshot snapshot = snapshots.get(code);
        if (snapshot == null || snapshot.applicability() != MetricApplicability.DEFINED) {
            return null;
        }
        return snapshot.components().get(component);
    }

    private static CandidateFacts screenerFacts(StrategyInputs in) {
        return new CandidateFacts(null, null, null, null, null, null, null, null, null, null, null, null,
                in.recentBars(), in.current(), Map.of(), false, Map.of());
    }

    // ── Enums ────────────────────────────────────────────────────────────────

    private enum AvailabilityStatus {
        OK(0), INSUFFICIENT(1), WITHHELD(2);

        private final int severity;

        AvailabilityStatus(int severity) {
            this.severity = severity;
        }

        int severity() {
            return severity;
        }
    }

    // ── Value objects ────────────────────────────────────────────────────────

    public record StrategyInputs(
            BigDecimal close,
            Map<IndicatorCode, IndicatorSnapshot> current,
            Map<IndicatorCode, IndicatorSnapshot> prior,
            List<DailyBarPoint> recentBars) {
        public StrategyInputs {
            current = current == null ? Map.of() : Map.copyOf(current);
            prior = prior == null ? Map.of() : Map.copyOf(prior);
            recentBars = recentBars == null ? List.of() : List.copyOf(recentBars);
        }
    }

    public record LevelSet(BigDecimal entryLow, BigDecimal entryHigh, BigDecimal stopLoss, BigDecimal target1,
            BigDecimal target2, BigDecimal riskReward) {
    }

    public enum EntryStatus {
        SIGNAL, NO_SIGNAL, INSUFFICIENT_HISTORY, WITHHELD
    }

    public record EntryEvaluation(StrategyCode strategyCode, EntryStatus status, String reasonCode,
            Direction direction, LevelSet levels, Map<String, String> supportingEvidence) {
        static EntryEvaluation signal(StrategyCode code, LevelSet levels, Map<String, String> evidence) {
            return new EntryEvaluation(code, EntryStatus.SIGNAL, null, Direction.LONG, levels, evidence);
        }

        static EntryEvaluation noSignal(StrategyCode code) {
            return new EntryEvaluation(code, EntryStatus.NO_SIGNAL, null, null, null, Map.of());
        }

        static EntryEvaluation insufficientHistory(StrategyCode code) {
            return new EntryEvaluation(code, EntryStatus.INSUFFICIENT_HISTORY, INSUFFICIENT_HISTORY, null, null,
                    Map.of());
        }

        static EntryEvaluation withheld(StrategyCode code, String reasonCode) {
            return new EntryEvaluation(code, EntryStatus.WITHHELD, reasonCode, null, null, Map.of());
        }
    }

    public record RiskFactorInputs(
            MetricPoint volatilityPercentOfClose,
            MetricPoint atr14Value,
            MetricPoint trailingAverageAtr14Value,
            MetricPoint highestCloseTrailing250,
            MetricPoint relativeVolume,
            MetricPoint regimeScore) {

        public record MetricPoint(BigDecimal value, boolean available, String reasonCode) {
            public static MetricPoint of(BigDecimal value) {
                return new MetricPoint(value, true, null);
            }

            public static MetricPoint unavailable(String reasonCode) {
                return new MetricPoint(null, false, reasonCode);
            }
        }
    }

    public record RiskFactorResult(RiskFactorCode factorCode, BigDecimal inputValue, Integer factorScore,
            MetricApplicability applicability, String reasonCode) {
    }

    public record RiskAssessment(Integer overallScore, RiskLevel riskLevel, SignalStrength signalStrength,
            List<RiskFactorResult> factors, List<String> reasonCodes) {
    }

    private record Availability(AvailabilityStatus status, String reasonCode) {
        static Availability ok() {
            return new Availability(AvailabilityStatus.OK, null);
        }

        static Availability insufficient() {
            return new Availability(AvailabilityStatus.INSUFFICIENT, INSUFFICIENT_HISTORY);
        }

        static Availability withheld(String reasonCode) {
            return new Availability(AvailabilityStatus.WITHHELD, reasonCode);
        }
    }
}

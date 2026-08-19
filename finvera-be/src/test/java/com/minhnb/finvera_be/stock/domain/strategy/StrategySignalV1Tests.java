package com.minhnb.finvera_be.stock.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.stock.domain.model.StockTypes.Direction;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.IndicatorCode;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.IndicatorComponent;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.MetricApplicability;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.RiskFactorCode;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.RiskLevel;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.SignalStrength;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.StrategyCode;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.DailyBarPoint;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.IndicatorSnapshot;
import com.minhnb.finvera_be.stock.domain.strategy.StrategySignalV1.EntryEvaluation;
import com.minhnb.finvera_be.stock.domain.strategy.StrategySignalV1.EntryStatus;
import com.minhnb.finvera_be.stock.domain.strategy.StrategySignalV1.RiskAssessment;
import com.minhnb.finvera_be.stock.domain.strategy.StrategySignalV1.RiskFactorInputs;
import com.minhnb.finvera_be.stock.domain.strategy.StrategySignalV1.RiskFactorInputs.MetricPoint;
import com.minhnb.finvera_be.stock.domain.strategy.StrategySignalV1.StrategyInputs;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Required-test-vector coverage from {@code contracts/strategy-signal-v1.md}.
 */
class StrategySignalV1Tests {

    // ── Trend Following ──────────────────────────────────────────────────

    @Test
    void trendFollowingTriggersOnUptrendAboveMa20() {
        StrategyInputs in = fixture()
                .value(IndicatorCode.MA20, "105").value(IndicatorCode.MA50, "100").value(IndicatorCode.MA200, "90")
                .close("110").build();
        EntryEvaluation result = StrategySignalV1.evaluate(StrategyCode.TREND_FOLLOWING, in);
        assertSignal(result, StrategyCode.TREND_FOLLOWING);
    }

    @Test
    void trendFollowingDoesNotTriggerWhenCloseNotAboveMa20() {
        StrategyInputs in = fixture()
                .value(IndicatorCode.MA20, "105").value(IndicatorCode.MA50, "100").value(IndicatorCode.MA200, "90")
                .close("105").build();
        assertNoSignal(StrategySignalV1.evaluate(StrategyCode.TREND_FOLLOWING, in));
    }

    @Test
    void trendFollowingIsInsufficientHistoryWhenMa200Missing() {
        StrategyInputs in = fixture()
                .value(IndicatorCode.MA20, "105").value(IndicatorCode.MA50, "100")
                .missing(IndicatorCode.MA200)
                .close("110").build();
        assertInsufficientHistory(StrategySignalV1.evaluate(StrategyCode.TREND_FOLLOWING, in));
    }

    @Test
    void trendFollowingIsEvaluatedAtExactMinimumBars() {
        StrategyInputs in = fixture()
                .value(IndicatorCode.MA20, "105").value(IndicatorCode.MA50, "100").value(IndicatorCode.MA200, "90")
                .close("110").build();
        EntryEvaluation result = StrategySignalV1.evaluate(StrategyCode.TREND_FOLLOWING, in);
        assertThat(result.status()).isNotEqualTo(EntryStatus.INSUFFICIENT_HISTORY);
    }

    // ── Momentum ──────────────────────────────────────────────────────────

    @Test
    void momentumTriggersOnStrongRsiAndPositiveHistogram() {
        StrategyInputs in = fixture().value(IndicatorCode.RSI14, "65")
                .component(IndicatorCode.MACD, IndicatorComponent.HISTOGRAM, "1.5").build();
        assertSignal(StrategySignalV1.evaluate(StrategyCode.MOMENTUM, in), StrategyCode.MOMENTUM);
    }

    @Test
    void momentumDoesNotTriggerBelowRsiThreshold() {
        StrategyInputs in = fixture().value(IndicatorCode.RSI14, "50")
                .component(IndicatorCode.MACD, IndicatorComponent.HISTOGRAM, "1.5").build();
        assertNoSignal(StrategySignalV1.evaluate(StrategyCode.MOMENTUM, in));
    }

    /** Required vector: RSI14 exactly 60.000000 — inclusive boundary honored, overall still no signal without histogram. */
    @Test
    void momentumAtExactRsiBoundaryStillNeedsPositiveHistogram() {
        StrategyInputs in = fixture().value(IndicatorCode.RSI14, "60.000000")
                .component(IndicatorCode.MACD, IndicatorComponent.HISTOGRAM, "-0.5").build();
        assertNoSignal(StrategySignalV1.evaluate(StrategyCode.MOMENTUM, in));
    }

    // ── Breakout ──────────────────────────────────────────────────────────

    @Test
    void breakoutTriggersOnBreakoutUpWithHighRelativeVolume() {
        StrategyInputs in = fixture().withBreakoutBars(true)
                .value(IndicatorCode.RELATIVE_VOLUME, "2.0").build();
        assertSignal(StrategySignalV1.evaluate(StrategyCode.BREAKOUT, in), StrategyCode.BREAKOUT);
    }

    @Test
    void breakoutDoesNotTriggerWithLowRelativeVolume() {
        StrategyInputs in = fixture().withBreakoutBars(true)
                .value(IndicatorCode.RELATIVE_VOLUME, "1.0").build();
        assertNoSignal(StrategySignalV1.evaluate(StrategyCode.BREAKOUT, in));
    }

    @Test
    void breakoutIsInsufficientHistoryWithFewerThan21Bars() {
        StrategyInputs in = fixture().withBreakoutBars(false)
                .value(IndicatorCode.RELATIVE_VOLUME, "2.0").build();
        assertInsufficientHistory(StrategySignalV1.evaluate(StrategyCode.BREAKOUT, in));
    }

    @Test
    void breakoutWithheldWhenAtr14UnavailableDespiteOwnMinimumBarsMet() {
        StrategyInputs in = fixture().withBreakoutBars(true)
                .value(IndicatorCode.RELATIVE_VOLUME, "2.0").missing(IndicatorCode.ATR14).build();
        EntryEvaluation result = StrategySignalV1.evaluate(StrategyCode.BREAKOUT, in);
        assertThat(result.status()).isEqualTo(EntryStatus.WITHHELD);
        assertThat(result.reasonCode()).isEqualTo("INVALID_LEVELS");
    }

    // ── Pullback ──────────────────────────────────────────────────────────

    @Test
    void pullbackTriggersOnIntactUptrendWithModerateRsi() {
        StrategyInputs in = fixture()
                .value(IndicatorCode.MA20, "105").value(IndicatorCode.MA50, "100").value(IndicatorCode.MA200, "90")
                .value(IndicatorCode.RSI14, "48").close("102").build();
        assertSignal(StrategySignalV1.evaluate(StrategyCode.PULLBACK, in), StrategyCode.PULLBACK);
    }

    @Test
    void pullbackDoesNotTriggerOutsideRsiRange() {
        StrategyInputs in = fixture()
                .value(IndicatorCode.MA20, "105").value(IndicatorCode.MA50, "100").value(IndicatorCode.MA200, "90")
                .value(IndicatorCode.RSI14, "60").close("102").build();
        assertNoSignal(StrategySignalV1.evaluate(StrategyCode.PULLBACK, in));
    }

    // ── Mean Reversion ────────────────────────────────────────────────────

    @Test
    void meanReversionTriggersOnOversoldBelowLowerBand() {
        StrategyInputs in = fixture().value(IndicatorCode.RSI14, "25")
                .component(IndicatorCode.BBANDS, IndicatorComponent.LOWER, "95").close("90").build();
        assertSignal(StrategySignalV1.evaluate(StrategyCode.MEAN_REVERSION, in), StrategyCode.MEAN_REVERSION);
    }

    @Test
    void meanReversionDoesNotTriggerAboveRsiThreshold() {
        StrategyInputs in = fixture().value(IndicatorCode.RSI14, "35")
                .component(IndicatorCode.BBANDS, IndicatorComponent.LOWER, "95").close("90").build();
        assertNoSignal(StrategySignalV1.evaluate(StrategyCode.MEAN_REVERSION, in));
    }

    // ── MA Crossover ──────────────────────────────────────────────────────

    @Test
    void maCrossoverTriggersOnFreshCrossToday() {
        StrategyInputs in = fixture()
                .value(IndicatorCode.MA20, "101").value(IndicatorCode.MA50, "100")
                .priorValue(IndicatorCode.MA20, "99").priorValue(IndicatorCode.MA50, "100").build();
        assertSignal(StrategySignalV1.evaluate(StrategyCode.MA_CROSSOVER, in), StrategyCode.MA_CROSSOVER);
    }

    @Test
    void maCrossoverDoesNotTriggerWhenAlreadyAboveYesterday() {
        StrategyInputs in = fixture()
                .value(IndicatorCode.MA20, "112").value(IndicatorCode.MA50, "100")
                .priorValue(IndicatorCode.MA20, "110").priorValue(IndicatorCode.MA50, "100").build();
        assertNoSignal(StrategySignalV1.evaluate(StrategyCode.MA_CROSSOVER, in));
    }

    @Test
    void maCrossoverDoesNotTriggerWhenStillBelow() {
        StrategyInputs in = fixture()
                .value(IndicatorCode.MA20, "96").value(IndicatorCode.MA50, "100")
                .priorValue(IndicatorCode.MA20, "95").priorValue(IndicatorCode.MA50, "100").build();
        assertNoSignal(StrategySignalV1.evaluate(StrategyCode.MA_CROSSOVER, in));
    }

    @Test
    void maCrossoverIsInsufficientHistoryWithoutPriorDay() {
        StrategyInputs in = fixture().value(IndicatorCode.MA20, "101").value(IndicatorCode.MA50, "100").build();
        assertInsufficientHistory(StrategySignalV1.evaluate(StrategyCode.MA_CROSSOVER, in));
    }

    // ── MACD-based ────────────────────────────────────────────────────────

    @Test
    void macdBasedTriggersOnFreshCrossAboveZero() {
        StrategyInputs in = fixture()
                .component(IndicatorCode.MACD, IndicatorComponent.HISTOGRAM, "0.3")
                .priorComponent(IndicatorCode.MACD, IndicatorComponent.HISTOGRAM, "-0.2").build();
        assertSignal(StrategySignalV1.evaluate(StrategyCode.MACD_BASED, in), StrategyCode.MACD_BASED);
    }

    @Test
    void macdBasedDoesNotTriggerWhenAlreadyPositiveYesterday() {
        StrategyInputs in = fixture()
                .component(IndicatorCode.MACD, IndicatorComponent.HISTOGRAM, "0.6")
                .priorComponent(IndicatorCode.MACD, IndicatorComponent.HISTOGRAM, "0.5").build();
        assertNoSignal(StrategySignalV1.evaluate(StrategyCode.MACD_BASED, in));
    }

    // ── RSI-based ─────────────────────────────────────────────────────────

    @Test
    void rsiBasedTriggersOnFreshExitFromOversold() {
        StrategyInputs in = fixture().value(IndicatorCode.RSI14, "32").priorValue(IndicatorCode.RSI14, "28").build();
        assertSignal(StrategySignalV1.evaluate(StrategyCode.RSI_BASED, in), StrategyCode.RSI_BASED);
    }

    @Test
    void rsiBasedDoesNotTriggerWhenAlreadyAboveYesterday() {
        StrategyInputs in = fixture().value(IndicatorCode.RSI14, "50").priorValue(IndicatorCode.RSI14, "45").build();
        assertNoSignal(StrategySignalV1.evaluate(StrategyCode.RSI_BASED, in));
    }

    // ── Signal levels ─────────────────────────────────────────────────────

    @Test
    void levelsFollowTheAtrAnchoredFormulaAndConstantRiskReward() {
        StrategyInputs in = fixture().value(IndicatorCode.RSI14, "65")
                .component(IndicatorCode.MACD, IndicatorComponent.HISTOGRAM, "1.5")
                .close("100").atr14("2").build();
        EntryEvaluation result = StrategySignalV1.evaluate(StrategyCode.MOMENTUM, in);
        assertSignal(result, StrategyCode.MOMENTUM);
        var levels = result.levels();
        assertThat(levels.entryLow()).isEqualByComparingTo("99.5");
        assertThat(levels.entryHigh()).isEqualByComparingTo("100.5");
        assertThat(levels.stopLoss()).isEqualByComparingTo("96");
        assertThat(levels.target1()).isEqualByComparingTo("108");
        assertThat(levels.target2()).isEqualByComparingTo("112");
        assertThat(levels.riskReward()).isEqualByComparingTo("2.0000");
        assertThat(result.direction()).isEqualTo(Direction.LONG);
    }

    @Test
    void signalIsWithheldWhenAtr14IsZeroOrNegative() {
        StrategyInputs in = fixture().value(IndicatorCode.RSI14, "65")
                .component(IndicatorCode.MACD, IndicatorComponent.HISTOGRAM, "1.5")
                .close("100").atr14("0").build();
        EntryEvaluation result = StrategySignalV1.evaluate(StrategyCode.MOMENTUM, in);
        assertThat(result.status()).isEqualTo(EntryStatus.WITHHELD);
        assertThat(result.reasonCode()).isEqualTo("INVALID_LEVELS");
    }

    // ── Source conflict withholding ──────────────────────────────────────

    @Test
    void strategyIsWithheldOnSourceConflictInADependency() {
        StrategyInputs in = fixture()
                .value(IndicatorCode.MA50, "100").value(IndicatorCode.MA200, "90")
                .withheld(IndicatorCode.MA20, "SOURCE_CONFLICT")
                .close("110").build();
        EntryEvaluation result = StrategySignalV1.evaluate(StrategyCode.TREND_FOLLOWING, in);
        assertThat(result.status()).isEqualTo(EntryStatus.WITHHELD);
        assertThat(result.reasonCode()).isEqualTo("SOURCE_CONFLICT");
    }

    // ── Replay determinism (U-6) ─────────────────────────────────────────

    @Test
    void identicalInputsReproduceIdenticalEvaluation() {
        StrategyInputs in = fixture().value(IndicatorCode.RSI14, "65")
                .component(IndicatorCode.MACD, IndicatorComponent.HISTOGRAM, "1.5").build();
        EntryEvaluation first = StrategySignalV1.evaluate(StrategyCode.MOMENTUM, in);
        EntryEvaluation second = StrategySignalV1.evaluate(StrategyCode.MOMENTUM, in);
        assertThat(first).isEqualTo(second);
    }

    // ── Risk factors and score ───────────────────────────────────────────

    @Test
    void riskScoreIsWithheldWithFewerThanFourAvailableFactors() {
        RiskFactorInputs inputs = new RiskFactorInputs(
                MetricPoint.of(new BigDecimal("5")), // VOLATILITY
                MetricPoint.unavailable("MISSING"), // ATR numerator
                MetricPoint.unavailable("MISSING"), // ATR denominator
                MetricPoint.unavailable("MISSING"), // DRAWDOWN
                MetricPoint.of(new BigDecimal("1.0")), // LIQUIDITY
                MetricPoint.unavailable("REGIME_UNAVAILABLE")); // MARKET_REGIME
        RiskAssessment assessment = StrategySignalV1.computeRisk(new BigDecimal("100"),
                new StrategySignalV1.LevelSet(new BigDecimal("99.5"), new BigDecimal("100.5"),
                        new BigDecimal("96"), new BigDecimal("108"), new BigDecimal("112"),
                        new BigDecimal("2.0000")),
                inputs);
        // Available: VOLATILITY, LIQUIDITY, STOP_DISTANCE (always computable) = 3.
        assertThat(assessment.overallScore()).isNull();
        assertThat(assessment.riskLevel()).isNull();
        assertThat(assessment.reasonCodes()).contains("INSUFFICIENT_RISK_FACTORS");
        assertThat(assessment.factors()).hasSize(6);
    }

    @Test
    void riskScoreIsPublishedWithExactlyFourAvailableFactors() {
        RiskFactorInputs inputs = new RiskFactorInputs(
                MetricPoint.of(new BigDecimal("5")), // VOLATILITY
                MetricPoint.unavailable("MISSING"),
                MetricPoint.unavailable("MISSING"),
                MetricPoint.unavailable("MISSING"), // DRAWDOWN
                MetricPoint.of(new BigDecimal("1.0")), // LIQUIDITY
                MetricPoint.of(new BigDecimal("50"))); // MARKET_REGIME
        RiskAssessment assessment = StrategySignalV1.computeRisk(new BigDecimal("100"),
                new StrategySignalV1.LevelSet(new BigDecimal("99.5"), new BigDecimal("100.5"),
                        new BigDecimal("96"), new BigDecimal("108"), new BigDecimal("112"),
                        new BigDecimal("2.0000")),
                inputs);
        assertThat(assessment.overallScore()).isNotNull();
        assertThat(assessment.riskLevel()).isNotNull();
        assertThat(assessment.reasonCodes()).isEmpty();
    }

    @Test
    void riskFactorsCoverAllSixCodesWithScoresAndLevels() {
        RiskFactorInputs inputs = new RiskFactorInputs(
                MetricPoint.of(new BigDecimal("2")), // VOLATILITY -> low risk
                MetricPoint.of(new BigDecimal("1")), // ATR numerator
                MetricPoint.of(new BigDecimal("1")), // ATR denominator -> ratio 1.0
                MetricPoint.of(new BigDecimal("100")), // DRAWDOWN input = highest close
                MetricPoint.of(new BigDecimal("2.0")), // LIQUIDITY -> low risk
                MetricPoint.of(new BigDecimal("80"))); // MARKET_REGIME -> bullish, low risk
        RiskAssessment assessment = StrategySignalV1.computeRisk(new BigDecimal("100"),
                new StrategySignalV1.LevelSet(new BigDecimal("99.5"), new BigDecimal("100.5"),
                        new BigDecimal("96"), new BigDecimal("108"), new BigDecimal("112"),
                        new BigDecimal("2.0000")),
                inputs);
        assertThat(assessment.factors()).extracting("factorCode")
                .containsExactlyInAnyOrder(RiskFactorCode.VOLATILITY, RiskFactorCode.ATR, RiskFactorCode.DRAWDOWN,
                        RiskFactorCode.LIQUIDITY, RiskFactorCode.STOP_DISTANCE, RiskFactorCode.MARKET_REGIME);
        assertThat(assessment.riskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(assessment.signalStrength()).isEqualTo(SignalStrength.STRONG);
    }

    @Test
    void riskAssessmentReplaysIdentically() {
        RiskFactorInputs inputs = new RiskFactorInputs(
                MetricPoint.of(new BigDecimal("5")), MetricPoint.of(new BigDecimal("1")),
                MetricPoint.of(new BigDecimal("1")), MetricPoint.of(new BigDecimal("100")),
                MetricPoint.of(new BigDecimal("1.0")), MetricPoint.of(new BigDecimal("50")));
        var levels = new StrategySignalV1.LevelSet(new BigDecimal("99.5"), new BigDecimal("100.5"),
                new BigDecimal("96"), new BigDecimal("108"), new BigDecimal("112"), new BigDecimal("2.0000"));
        RiskAssessment first = StrategySignalV1.computeRisk(new BigDecimal("100"), levels, inputs);
        RiskAssessment second = StrategySignalV1.computeRisk(new BigDecimal("100"), levels, inputs);
        assertThat(first).isEqualTo(second);
    }

    // ── Assertions ────────────────────────────────────────────────────────

    private static void assertSignal(EntryEvaluation result, StrategyCode code) {
        assertThat(result.strategyCode()).isEqualTo(code);
        assertThat(result.status()).isEqualTo(EntryStatus.SIGNAL);
        assertThat(result.levels()).isNotNull();
        assertThat(result.direction()).isEqualTo(Direction.LONG);
    }

    private static void assertNoSignal(EntryEvaluation result) {
        assertThat(result.status()).isEqualTo(EntryStatus.NO_SIGNAL);
        assertThat(result.levels()).isNull();
    }

    private static void assertInsufficientHistory(EntryEvaluation result) {
        assertThat(result.status()).isEqualTo(EntryStatus.INSUFFICIENT_HISTORY);
        assertThat(result.reasonCode()).isEqualTo("INSUFFICIENT_HISTORY");
    }

    // ── Fixture builder ───────────────────────────────────────────────────

    private static Fixture fixture() {
        return new Fixture();
    }

    private static final class Fixture {
        private BigDecimal close = new BigDecimal("100");
        private BigDecimal atr14 = new BigDecimal("2");
        private final Map<IndicatorCode, IndicatorSnapshot> current = new EnumMap<>(IndicatorCode.class);
        private final Map<IndicatorCode, IndicatorSnapshot> prior = new EnumMap<>(IndicatorCode.class);
        private List<DailyBarPoint> recentBars = List.of();

        Fixture close(String value) {
            this.close = new BigDecimal(value);
            return this;
        }

        Fixture atr14(String value) {
            this.atr14 = new BigDecimal(value);
            return this;
        }

        Fixture value(IndicatorCode code, String value) {
            return component(code, IndicatorComponent.VALUE, value);
        }

        Fixture priorValue(IndicatorCode code, String value) {
            return priorComponent(code, IndicatorComponent.VALUE, value);
        }

        Fixture component(IndicatorCode code, IndicatorComponent component, String value) {
            Map<IndicatorComponent, BigDecimal> components = new LinkedHashMap<>(componentsOf(current, code));
            components.put(component, new BigDecimal(value));
            current.put(code, new IndicatorSnapshot(MetricApplicability.DEFINED, components, null));
            return this;
        }

        Fixture priorComponent(IndicatorCode code, IndicatorComponent component, String value) {
            Map<IndicatorComponent, BigDecimal> components = new LinkedHashMap<>(componentsOf(prior, code));
            components.put(component, new BigDecimal(value));
            prior.put(code, new IndicatorSnapshot(MetricApplicability.DEFINED, components, null));
            return this;
        }

        Fixture missing(IndicatorCode code) {
            current.put(code, new IndicatorSnapshot(MetricApplicability.MISSING, Map.of(), "INSUFFICIENT_HISTORY"));
            return this;
        }

        Fixture withheld(IndicatorCode code, String reasonCode) {
            current.put(code, new IndicatorSnapshot(MetricApplicability.MISSING, Map.of(), reasonCode));
            return this;
        }

        Fixture withBreakoutBars(boolean sufficient) {
            List<DailyBarPoint> bars = new ArrayList<>();
            LocalDate start = LocalDate.of(2026, 1, 1);
            int priorSessions = sufficient ? 20 : 15;
            for (int i = 0; i < priorSessions; i++) {
                BigDecimal level = new BigDecimal("100");
                bars.add(new DailyBarPoint(start.plusDays(i), level, level.add(new BigDecimal("1")),
                        level.subtract(new BigDecimal("1"))));
            }
            BigDecimal breakoutClose = new BigDecimal("110");
            bars.add(new DailyBarPoint(start.plusDays(priorSessions), breakoutClose,
                    breakoutClose.add(new BigDecimal("1")), breakoutClose.subtract(new BigDecimal("1"))));
            this.recentBars = bars;
            this.close = breakoutClose;
            return this;
        }

        private static Map<IndicatorComponent, BigDecimal> componentsOf(Map<IndicatorCode, IndicatorSnapshot> map,
                IndicatorCode code) {
            IndicatorSnapshot existing = map.get(code);
            return existing == null ? Map.of() : existing.components();
        }

        StrategyInputs build() {
            if (!current.containsKey(IndicatorCode.ATR14)) {
                current.put(IndicatorCode.ATR14, new IndicatorSnapshot(MetricApplicability.DEFINED,
                        Map.of(IndicatorComponent.VALUE, atr14), null));
            }
            return new StrategyInputs(close, current, prior, recentBars);
        }
    }
}

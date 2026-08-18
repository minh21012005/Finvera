package com.minhnb.finvera_be.stock.domain.technical;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.stock.domain.model.StockTypes.IndicatorCode;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.IndicatorComponent;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.MetricApplicability;
import com.minhnb.finvera_be.stock.domain.technical.TechnicalIndicatorsV1.ComponentResult;
import com.minhnb.finvera_be.stock.domain.technical.TechnicalIndicatorsV1.IndicatorResult;
import com.minhnb.finvera_be.stock.domain.technical.TechnicalIndicatorsV1.IndicatorSetResult;
import com.minhnb.finvera_be.stock.domain.technical.TechnicalIndicatorsV1.TechnicalBar;
import com.minhnb.finvera_be.stock.provider.StockHistoryProvider.DailyBar;
import com.minhnb.finvera_be.stock.provider.fixture.FixtureStockHistoryProvider;
import com.minhnb.finvera_be.stock.provider.fixture.FixtureStockHistoryProvider.FixtureScenario;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code contracts/technical-indicators-v1.md} required-test-vector table.
 * Golden/flat/monotonic expected values come from
 * {@code tools/market-data/fixture-gen/generate_stock_technical_fixtures.py},
 * an independent Python {@code Decimal} implementation of the same formulas,
 * so this is a real cross-check rather than a copy of the engine under test.
 */
class TechnicalIndicatorsV1Tests {

    private final TechnicalIndicatorsV1 engine = new TechnicalIndicatorsV1();

    @Test
    void goldenVectorMatchesTheIndependentlyComputedExpectedValues() {
        IndicatorSetResult result = engine.compute(loadBars(FixtureScenario.GOLDEN_250));
        Map<IndicatorCode, IndicatorResult> byCode = indexByCode(result);

        assertComponent(byCode, IndicatorCode.MA20, IndicatorComponent.VALUE, "41491.754764850000");
        assertComponent(byCode, IndicatorCode.MA50, IndicatorComponent.VALUE, "42765.281672260000");
        assertComponent(byCode, IndicatorCode.MA200, IndicatorComponent.VALUE, "45657.568713360000");
        assertComponent(byCode, IndicatorCode.RSI14, IndicatorComponent.VALUE, "68.769871598355");
        assertComponent(byCode, IndicatorCode.MACD, IndicatorComponent.MACD_LINE, "563.949947612104");
        assertComponent(byCode, IndicatorCode.MACD, IndicatorComponent.SIGNAL, "26.533251001545");
        assertComponent(byCode, IndicatorCode.MACD, IndicatorComponent.HISTOGRAM, "537.416696610559");
        assertComponent(byCode, IndicatorCode.BBANDS, IndicatorComponent.UPPER, "45163.777696355250");
        assertComponent(byCode, IndicatorCode.BBANDS, IndicatorComponent.MIDDLE, "41491.754764850000");
        assertComponent(byCode, IndicatorCode.BBANDS, IndicatorComponent.LOWER, "37819.731833344750");
        assertComponent(byCode, IndicatorCode.BBANDS, IndicatorComponent.BANDWIDTH, "17.700012700400");
        assertComponent(byCode, IndicatorCode.ATR14, IndicatorComponent.VALUE, "774.794171227481");
        assertComponent(byCode, IndicatorCode.ATR14, IndicatorComponent.PERCENT_OF_CLOSE, "1.736465041100");
        assertComponent(byCode, IndicatorCode.AVG_VOLUME20, IndicatorComponent.VALUE, "2243224.800000000000");
        assertComponent(byCode, IndicatorCode.RELATIVE_VOLUME, IndicatorComponent.VALUE, "0.965829257543");

        List<TechnicalBar> allBars = loadBars(FixtureScenario.GOLDEN_250);
        IndicatorResult ma200 = byCode.get(IndicatorCode.MA200);
        assertThat(ma200.windowStartDate()).isEqualTo(allBars.get(allBars.size() - 200).tradingDate());
        assertThat(ma200.windowEndDate()).isEqualTo(LocalDate.parse("2026-08-14"));
        assertThat(ma200.inputSetHash()).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void flatSeriesProducesNeutralRsiZeroMacdAndZeroAtr() {
        Map<IndicatorCode, IndicatorResult> byCode = indexByCode(engine.compute(loadBars(FixtureScenario.FLAT)));

        assertComponent(byCode, IndicatorCode.RSI14, IndicatorComponent.VALUE, "50.000000000000");
        assertComponent(byCode, IndicatorCode.MACD, IndicatorComponent.MACD_LINE, "0.000000000000");
        assertComponent(byCode, IndicatorCode.MACD, IndicatorComponent.SIGNAL, "0.000000000000");
        assertComponent(byCode, IndicatorCode.MACD, IndicatorComponent.HISTOGRAM, "0.000000000000");
        assertComponent(byCode, IndicatorCode.ATR14, IndicatorComponent.VALUE, "0.000000000000");
        assertComponent(byCode, IndicatorCode.ATR14, IndicatorComponent.PERCENT_OF_CLOSE, "0.000000000000");
    }

    @Test
    void monotonicRisingSeriesProducesMaximalRsiAndStrictlyAscendingMovingAverages() {
        List<TechnicalBar> bars = loadBars(FixtureScenario.MONOTONIC_RISING);
        Map<IndicatorCode, IndicatorResult> byCode = indexByCode(engine.compute(bars));
        assertComponent(byCode, IndicatorCode.RSI14, IndicatorComponent.VALUE, "100.000000000000");

        BigDecimal ma20AtN = valueOf(byCode, IndicatorCode.MA20, IndicatorComponent.VALUE);
        BigDecimal ma20AtNMinus1 = valueOf(indexByCode(engine.compute(bars.subList(0, bars.size() - 1))),
                IndicatorCode.MA20, IndicatorComponent.VALUE);
        BigDecimal ma20AtNMinus2 = valueOf(indexByCode(engine.compute(bars.subList(0, bars.size() - 2))),
                IndicatorCode.MA20, IndicatorComponent.VALUE);
        assertThat(ma20AtN).isGreaterThan(ma20AtNMinus1);
        assertThat(ma20AtNMinus1).isGreaterThan(ma20AtNMinus2);

        BigDecimal ma50AtN = valueOf(byCode, IndicatorCode.MA50, IndicatorComponent.VALUE);
        BigDecimal ma50AtNMinus1 = valueOf(indexByCode(engine.compute(bars.subList(0, bars.size() - 1))),
                IndicatorCode.MA50, IndicatorComponent.VALUE);
        assertThat(ma50AtN).isGreaterThan(ma50AtNMinus1);

        BigDecimal ma200AtN = valueOf(byCode, IndicatorCode.MA200, IndicatorComponent.VALUE);
        BigDecimal ma200AtNMinus1 = valueOf(indexByCode(engine.compute(bars.subList(0, bars.size() - 1))),
                IndicatorCode.MA200, IndicatorComponent.VALUE);
        assertThat(ma200AtN).isGreaterThan(ma200AtNMinus1);
    }

    @Test
    void barCountBoundary19Versus20FlipsMa20Bbands19UnavailableAnd20Available() {
        IndicatorResult unavailable = indexByCode(engine.compute(loadBars(FixtureScenario.BARS_19)))
                .get(IndicatorCode.MA20);
        assertThat(unavailable.applicability()).isEqualTo(MetricApplicability.MISSING);
        assertThat(unavailable.reasonCode()).isEqualTo("INSUFFICIENT_HISTORY");
        assertThat(unavailable.requiredBars()).isEqualTo(20);
        assertThat(unavailable.availableBars()).isEqualTo(19);
        assertThat(unavailable.components()).isEmpty();

        IndicatorResult available = indexByCode(engine.compute(loadBars(FixtureScenario.BARS_20)))
                .get(IndicatorCode.MA20);
        assertThat(available.applicability()).isEqualTo(MetricApplicability.DEFINED);
        assertThat(available.components()).isNotEmpty();

        Map<IndicatorCode, IndicatorResult> bbands19 = indexByCode(engine.compute(loadBars(FixtureScenario.BARS_19)));
        assertThat(bbands19.get(IndicatorCode.BBANDS).applicability()).isEqualTo(MetricApplicability.MISSING);
        Map<IndicatorCode, IndicatorResult> bbands20 = indexByCode(engine.compute(loadBars(FixtureScenario.BARS_20)));
        assertThat(bbands20.get(IndicatorCode.BBANDS).applicability()).isEqualTo(MetricApplicability.DEFINED);
    }

    @Test
    void barCountBoundary20Versus21FlipsRelativeVolume() {
        IndicatorResult unavailable = indexByCode(engine.compute(loadBars(FixtureScenario.BARS_20)))
                .get(IndicatorCode.RELATIVE_VOLUME);
        assertThat(unavailable.applicability()).isEqualTo(MetricApplicability.MISSING);
        assertThat(unavailable.requiredBars()).isEqualTo(21);

        IndicatorResult available = indexByCode(engine.compute(loadBars(FixtureScenario.BARS_21)))
                .get(IndicatorCode.RELATIVE_VOLUME);
        assertThat(available.applicability()).isEqualTo(MetricApplicability.DEFINED);
    }

    @Test
    void barCountBoundary49Versus50FlipsMa50() {
        assertThat(indexByCode(engine.compute(loadBars(FixtureScenario.BARS_49))).get(IndicatorCode.MA50)
                .applicability()).isEqualTo(MetricApplicability.MISSING);
        assertThat(indexByCode(engine.compute(loadBars(FixtureScenario.BARS_50))).get(IndicatorCode.MA50)
                .applicability()).isEqualTo(MetricApplicability.DEFINED);
    }

    @Test
    void barCountBoundary199Versus200FlipsMa200() {
        assertThat(indexByCode(engine.compute(loadBars(FixtureScenario.BARS_199))).get(IndicatorCode.MA200)
                .applicability()).isEqualTo(MetricApplicability.MISSING);
        assertThat(indexByCode(engine.compute(loadBars(FixtureScenario.BARS_200))).get(IndicatorCode.MA200)
                .applicability()).isEqualTo(MetricApplicability.DEFINED);
    }

    @Test
    void barCountBoundary249Versus250FlipsTheFixedWindowIndicators() {
        Map<IndicatorCode, IndicatorResult> at249 = indexByCode(engine.compute(loadBars(FixtureScenario.BARS_249)));
        assertThat(at249.get(IndicatorCode.RSI14).applicability()).isEqualTo(MetricApplicability.MISSING);
        assertThat(at249.get(IndicatorCode.MACD).applicability()).isEqualTo(MetricApplicability.MISSING);
        assertThat(at249.get(IndicatorCode.ATR14).applicability()).isEqualTo(MetricApplicability.MISSING);

        Map<IndicatorCode, IndicatorResult> at250 = indexByCode(engine.compute(loadBars(FixtureScenario.BARS_250)));
        assertThat(at250.get(IndicatorCode.RSI14).applicability()).isEqualTo(MetricApplicability.DEFINED);
        assertThat(at250.get(IndicatorCode.MACD).applicability()).isEqualTo(MetricApplicability.DEFINED);
        assertThat(at250.get(IndicatorCode.ATR14).applicability()).isEqualTo(MetricApplicability.DEFINED);
    }

    @Test
    void independenceAnUnavailableMa200NeverSuppressesMa20OrMa50() {
        Map<IndicatorCode, IndicatorResult> byCode = indexByCode(engine.compute(loadBars(FixtureScenario.BARS_199)));
        assertThat(byCode.get(IndicatorCode.MA200).applicability()).isEqualTo(MetricApplicability.MISSING);
        assertThat(byCode.get(IndicatorCode.MA20).applicability()).isEqualTo(MetricApplicability.DEFINED);
        assertThat(byCode.get(IndicatorCode.MA50).applicability()).isEqualTo(MetricApplicability.DEFINED);
    }

    @Test
    void splitInWindowRawAndAdjustedRunsDifferAndNeitherMixesBases() {
        // The engine itself never inspects adjustment_status (rule U-2's basis
        // selection is the caller's job, per this class's Javadoc); what this test
        // proves is that feeding it the raw run versus the adjusted run of the same
        // split fixture — each internally consistent, per T022's no-splice guard —
        // yields two genuinely different, self-consistent results, not that the
        // engine ever mixes the two within one run.
        JsonNode root = readFixture("fixtures/stock/chart/chart-split-in-window.json");
        List<TechnicalBar> rawRun = parseSeries(root.path("rawSeries").path("bars"));
        List<TechnicalBar> adjustedRun = parseSeries(root.path("adjustedSeries").path("bars"));
        assertThat(rawRun).hasSameSizeAs(adjustedRun);

        // MA20 alone sits entirely after the ex-date in this fixture (the split is
        // at index 30 of 60), so it is identical in both bases by construction — not
        // a bug. MA50's window spans the ex-date, so it is the vector that actually
        // exercises "the two runs differ."
        BigDecimal ma50FromRaw = valueOf(indexByCode(engine.compute(rawRun)), IndicatorCode.MA50,
                IndicatorComponent.VALUE);
        BigDecimal ma50FromAdjusted = valueOf(indexByCode(engine.compute(adjustedRun)), IndicatorCode.MA50,
                IndicatorComponent.VALUE);
        assertThat(ma50FromRaw).isNotEqualByComparingTo(ma50FromAdjusted);
    }

    @Test
    void zeroDenominatorsReturnNotApplicableNeverAnExceptionAndNeverZero() {
        // Bandwidth: last 20 closes are all zero, so the population mean (MIDDLE) is
        // zero — a degenerate-but-legal input, not something ingestion would ever
        // accept, but the engine must still not divide by zero.
        List<TechnicalBar> zeroCloseBars = syntheticBars(20, i -> BigDecimal.ZERO, i -> 1_000_000L);
        IndicatorResult bbands = indexByCode(engine.compute(zeroCloseBars)).get(IndicatorCode.BBANDS);
        ComponentResult bandwidth = componentOf(bbands, IndicatorComponent.BANDWIDTH);
        assertThat(bandwidth.applicability()).isEqualTo(MetricApplicability.NOT_APPLICABLE);
        assertThat(bandwidth.value()).isNull();
        assertThat(componentOf(bbands, IndicatorComponent.UPPER).applicability())
                .isEqualTo(MetricApplicability.DEFINED);

        // ATR percent-of-close: the as-of close is zero.
        List<TechnicalBar> zeroFinalClose = syntheticBars(250,
                i -> i == 249 ? BigDecimal.ZERO : new BigDecimal("100.000000"), i -> 1_000_000L);
        IndicatorResult atr = indexByCode(engine.compute(zeroFinalClose)).get(IndicatorCode.ATR14);
        ComponentResult percentOfClose = componentOf(atr, IndicatorComponent.PERCENT_OF_CLOSE);
        assertThat(percentOfClose.applicability()).isEqualTo(MetricApplicability.NOT_APPLICABLE);
        assertThat(percentOfClose.value()).isNull();
        assertThat(componentOf(atr, IndicatorComponent.VALUE).applicability()).isEqualTo(MetricApplicability.DEFINED);

        // Relative volume: the whole prior 20-day window is suspended (zero volume).
        List<TechnicalBar> suspendedPriorWindow = syntheticBars(21, i -> new BigDecimal("100.000000"),
                i -> i < 20 ? 0L : 5_000_000L);
        IndicatorResult relativeVolume = indexByCode(engine.compute(suspendedPriorWindow))
                .get(IndicatorCode.RELATIVE_VOLUME);
        assertThat(relativeVolume.applicability()).isEqualTo(MetricApplicability.NOT_APPLICABLE);
        assertThat(componentOf(relativeVolume, IndicatorComponent.VALUE).value()).isNull();
    }

    @Test
    void replayDeterminismRecomputingTheSameBarsYieldsExactlyTheStoredDecimal() {
        List<TechnicalBar> bars = loadBars(FixtureScenario.GOLDEN_250);
        IndicatorSetResult first = engine.compute(bars);
        IndicatorSetResult second = engine.compute(bars);

        Map<IndicatorCode, IndicatorResult> byCodeFirst = indexByCode(first);
        Map<IndicatorCode, IndicatorResult> byCodeSecond = indexByCode(second);
        for (IndicatorCode code : IndicatorCode.values()) {
            IndicatorResult a = byCodeFirst.get(code);
            IndicatorResult b = byCodeSecond.get(code);
            assertThat(a.inputSetHash()).isEqualTo(b.inputSetHash());
            assertThat(a.components()).hasSameSizeAs(b.components());
            for (int i = 0; i < a.components().size(); i++) {
                BigDecimal va = a.components().get(i).value();
                BigDecimal vb = b.components().get(i).value();
                if (va == null) {
                    assertThat(vb).isNull();
                } else {
                    assertThat(va).isEqualByComparingTo(vb);
                }
            }
        }
    }

    private static void assertComponent(Map<IndicatorCode, IndicatorResult> byCode, IndicatorCode code,
            IndicatorComponent componentCode, String expected) {
        assertThat(valueOf(byCode, code, componentCode)).isEqualByComparingTo(expected);
    }

    private static BigDecimal valueOf(Map<IndicatorCode, IndicatorResult> byCode, IndicatorCode code,
            IndicatorComponent componentCode) {
        return componentOf(byCode.get(code), componentCode).value();
    }

    private static ComponentResult componentOf(IndicatorResult result, IndicatorComponent componentCode) {
        return result.components().stream().filter(c -> c.componentCode() == componentCode).findFirst()
                .orElseThrow(() -> new AssertionError("Missing component " + componentCode + " on " + result));
    }

    private static Map<IndicatorCode, IndicatorResult> indexByCode(IndicatorSetResult result) {
        return result.indicators().stream()
                .collect(java.util.stream.Collectors.toMap(IndicatorResult::indicatorCode, r -> r));
    }

    private static List<TechnicalBar> loadBars(FixtureScenario scenario) {
        return toTechnicalBars(new FixtureStockHistoryProvider(scenario).getDailyBars("FPT", LocalDate.MIN,
                LocalDate.MAX));
    }

    private static List<TechnicalBar> toTechnicalBars(List<DailyBar> bars) {
        return bars.stream()
                .map(bar -> new TechnicalBar(bar.tradingDate(), bar.close(), bar.high(), bar.low(), bar.volume()))
                .toList();
    }

    private static JsonNode readFixture(String classpathLocation) {
        try (InputStream input = new ClassPathResource(classpathLocation).getInputStream()) {
            return JsonMapper.builder().build().readTree(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read fixture: " + classpathLocation, exception);
        }
    }

    private static List<TechnicalBar> parseSeries(JsonNode barsArray) {
        List<TechnicalBar> bars = new java.util.ArrayList<>();
        for (JsonNode bar : barsArray) {
            bars.add(new TechnicalBar(LocalDate.parse(bar.path("tradingDate").stringValue()),
                    new BigDecimal(bar.path("close").stringValue()), new BigDecimal(bar.path("high").stringValue()),
                    new BigDecimal(bar.path("low").stringValue()), bar.path("volume").longValue()));
        }
        return bars;
    }

    private static List<TechnicalBar> syntheticBars(int count, java.util.function.IntFunction<BigDecimal> close,
            java.util.function.IntFunction<Long> volume) {
        LocalDate start = LocalDate.of(2020, 1, 1);
        List<TechnicalBar> bars = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            BigDecimal c = close.apply(i);
            bars.add(new TechnicalBar(start.plusDays(i), c, c.add(new BigDecimal("1.000000")),
                    c.subtract(new BigDecimal("1.000000")), volume.apply(i)));
        }
        return bars;
    }
}

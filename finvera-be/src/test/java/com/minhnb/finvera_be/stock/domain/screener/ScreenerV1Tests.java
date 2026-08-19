package com.minhnb.finvera_be.stock.domain.screener;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.IndicatorCode;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.IndicatorComponent;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.MetricApplicability;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.ValuationMetricCode;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.BreakoutCondition;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.CandidateFacts;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.CandidateResult;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.CategoryStatus;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.DailyBarPoint;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.FundamentalFilter;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.IndicatorSnapshot;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.MacdSignal;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.MarketFilter;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.MaRelationship;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.MetricPoint;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.PriceFilter;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.ScreenCriteria;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.TechnicalFilter;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.TrendDirection;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@code contracts/screener-v1.md} required-test-vector suite. Red before
 * {@link ScreenerV1} exists (Feature 003 T004/T005).
 */
class ScreenerV1Tests {

    private static final UUID SECTOR_A = UUID.randomUUID();
    private static final UUID SECTOR_B = UUID.randomUUID();

    // ── Market ───────────────────────────────────────────────────────────────

    @Test
    void exchangeFilterMatchesOnlyTheSelectedExchanges() {
        CandidateFacts hose = baseCandidate("A", "HOSE").build();
        CandidateFacts hnx = baseCandidate("B", "HNX").build();
        ScreenCriteria criteria = criteria(new MarketFilter(Set_of("HOSE"), null, null, null), null, null, null);

        assertThat(ScreenerV1.evaluate(hose, criteria).matched()).isTrue();
        assertThat(ScreenerV1.evaluate(hnx, criteria).matched()).isFalse();
    }

    @Test
    void sectorFilterExcludesNullSectorWithReason() {
        CandidateFacts noSector = baseCandidate("A", "HOSE").sectorId(null, null).build();
        ScreenCriteria criteria = criteria(new MarketFilter(null, Set_of(SECTOR_A), null, null), null, null, null);

        CandidateResult result = ScreenerV1.evaluate(noSector, criteria);
        assertThat(result.matched()).isFalse();
        assertThat(onlyOutcome(result).status()).isEqualTo(CategoryStatus.UNAVAILABLE);
        assertThat(onlyOutcome(result).reasonCode()).isEqualTo("SECTOR_UNCLASSIFIED");
    }

    @Test
    void marketCapRangeUsesCloseTimesSharesOutstanding() {
        // close = 50000, shares = 1_000_000 -> marketCap = 50,000,000,000
        CandidateFacts c = baseCandidate("A", "HOSE").latestClose("50000").sharesOutstanding(1_000_000L).build();
        ScreenCriteria within = criteria(
                new MarketFilter(null, null, new BigDecimal("40000000000"), new BigDecimal("60000000000")),
                null, null, null);
        ScreenCriteria outside = criteria(
                new MarketFilter(null, null, new BigDecimal("60000000000"), new BigDecimal("70000000000")),
                null, null, null);

        CandidateResult resultWithin = ScreenerV1.evaluate(c, within);
        assertThat(resultWithin.matched()).isTrue();
        assertThat(resultWithin.matchedValues().get("marketCap")).isEqualTo("50000000000.000000");
        assertThat(ScreenerV1.evaluate(c, outside).matched()).isFalse();
    }

    @Test
    void marketCapFilterExcludesNullSharesOutstandingWithReason() {
        CandidateFacts c = baseCandidate("A", "HOSE").sharesOutstanding(null).build();
        ScreenCriteria criteria = criteria(
                new MarketFilter(null, null, BigDecimal.ZERO, new BigDecimal("999999999999")), null, null, null);

        CandidateResult result = ScreenerV1.evaluate(c, criteria);
        assertThat(onlyOutcome(result).status()).isEqualTo(CategoryStatus.UNAVAILABLE);
        assertThat(onlyOutcome(result).reasonCode()).isEqualTo("SHARES_OUTSTANDING_MISSING");
    }

    // ── Price ────────────────────────────────────────────────────────────────

    @Test
    void priceRangeExcludesUnavailablePriceWithReason() {
        CandidateFacts c = baseCandidate("A", "HOSE").latestClose((BigDecimal) null).build();
        ScreenCriteria criteria = criteria(null,
                new PriceFilter(BigDecimal.ONE, new BigDecimal("999999"), null, null), null, null);

        CandidateResult result = ScreenerV1.evaluate(c, criteria);
        assertThat(onlyOutcome(result).status()).isEqualTo(CategoryStatus.UNAVAILABLE);
        assertThat(onlyOutcome(result).reasonCode()).isEqualTo("PRICE_UNAVAILABLE");
    }

    @Test
    void priceChangePercentUsesPreviousValidCloseBasis() {
        // last=110, prior=100 -> +10.000000%
        CandidateFacts c = baseCandidate("A", "HOSE").latestClose("110").previousValidClose("100").build();
        ScreenCriteria criteria = criteria(null,
                new PriceFilter(null, null, new BigDecimal("5"), new BigDecimal("15")), null, null);

        CandidateResult result = ScreenerV1.evaluate(c, criteria);
        assertThat(result.matched()).isTrue();
        assertThat(result.matchedValues().get("priceChangePercent")).isEqualTo("10.000000");
    }

    // ── MACD signal ──────────────────────────────────────────────────────────

    @Test
    void macdHistogramExactlyZeroIsNeutralNotBullishOrBearish() {
        CandidateFacts c = baseCandidate("A", "HOSE")
                .indicator(IndicatorCode.MACD, Map.of(IndicatorComponent.HISTOGRAM, BigDecimal.ZERO))
                .build();
        ScreenCriteria bullish = criteria(null, null,
                new TechnicalFilter(null, null, MacdSignal.BULLISH, null, null, null, null, null, null, null), null);
        ScreenCriteria neutral = criteria(null, null,
                new TechnicalFilter(null, null, MacdSignal.NEUTRAL, null, null, null, null, null, null, null), null);

        assertThat(ScreenerV1.evaluate(c, bullish).matched()).isFalse();
        assertThat(ScreenerV1.evaluate(c, neutral).matched()).isTrue();
    }

    // ── MA relationship ──────────────────────────────────────────────────────

    @Test
    void priceAboveMa50MatchesOnlyWhenStrictlyAbove() {
        CandidateFacts above = baseCandidate("A", "HOSE").latestClose("100")
                .indicator(IndicatorCode.MA50, Map.of(IndicatorComponent.VALUE, new BigDecimal("90"))).build();
        CandidateFacts below = baseCandidate("A", "HOSE").latestClose("80")
                .indicator(IndicatorCode.MA50, Map.of(IndicatorComponent.VALUE, new BigDecimal("90"))).build();
        ScreenCriteria criteria = criteria(null, null,
                new TechnicalFilter(null, null, null, Set_of(MaRelationship.PRICE_ABOVE_MA50), null, null, null,
                        null, null, null),
                null);

        assertThat(ScreenerV1.evaluate(above, criteria).matched()).isTrue();
        assertThat(ScreenerV1.evaluate(below, criteria).matched()).isFalse();
    }

    // ── Volume vs Relative volume ────────────────────────────────────────────

    @Test
    void volumeReadsRawSessionVolumeDistinctFromRelativeVolume() {
        CandidateFacts c = baseCandidate("A", "HOSE").latestVolume(500_000L)
                .indicator(IndicatorCode.RELATIVE_VOLUME, Map.of(IndicatorComponent.VALUE, new BigDecimal("2.5")))
                .build();
        ScreenCriteria volumeCriteria = criteria(null, null,
                new TechnicalFilter(null, null, null, null, 400_000L, 600_000L, null, null, null, null), null);
        ScreenCriteria relativeCriteria = criteria(null, null,
                new TechnicalFilter(null, null, null, null, null, null, new BigDecimal("2"), new BigDecimal("3"),
                        null, null),
                null);

        assertThat(ScreenerV1.evaluate(c, volumeCriteria).matchedValues()).containsEntry("volume", "500000");
        assertThat(ScreenerV1.evaluate(c, relativeCriteria).matchedValues()).containsEntry("relativeVolume", "2.5");
    }

    // ── Breakout ─────────────────────────────────────────────────────────────

    @Test
    void breakoutAtExactly20SessionsIsInsufficientHistory() {
        CandidateFacts c = baseCandidate("A", "HOSE").recentBars(flatBars(20, "100")).build();
        ScreenCriteria criteria = criteria(null, null,
                new TechnicalFilter(null, null, null, null, null, null, null, null, BreakoutCondition.BREAKOUT_UP,
                        null),
                null);

        CandidateResult result = ScreenerV1.evaluate(c, criteria);
        assertThat(onlyOutcome(result).status()).isEqualTo(CategoryStatus.UNAVAILABLE);
        assertThat(onlyOutcome(result).reasonCode()).isEqualTo("INSUFFICIENT_HISTORY");
    }

    @Test
    void breakoutAtExactly21SessionsIsEvaluated() {
        List<DailyBarPoint> bars = new ArrayList<>(flatBars(20, "100"));
        bars.add(new DailyBarPoint(LocalDate.of(2026, 8, 19), new BigDecimal("150"), new BigDecimal("150"),
                new BigDecimal("140")));
        CandidateFacts c = baseCandidate("A", "HOSE").recentBars(bars).build();
        ScreenCriteria criteria = criteria(null, null,
                new TechnicalFilter(null, null, null, null, null, null, null, null, BreakoutCondition.BREAKOUT_UP,
                        null),
                null);

        assertThat(ScreenerV1.evaluate(c, criteria).matched()).isTrue();
    }

    @Test
    void breakoutTieAtPriorHighIsNoneNotBreakoutUp() {
        List<DailyBarPoint> bars = new ArrayList<>(flatBars(20, "100"));
        bars.add(new DailyBarPoint(LocalDate.of(2026, 8, 19), new BigDecimal("100"), new BigDecimal("100"),
                new BigDecimal("100")));
        CandidateFacts c = baseCandidate("A", "HOSE").recentBars(bars).build();
        var breakout = ScreenerV1.deriveBreakout(c);
        assertThat(breakout.unavailable()).isFalse();
        assertThat(breakout.condition()).isEqualTo(BreakoutCondition.NONE);
    }

    // ── Trend ────────────────────────────────────────────────────────────────

    @Test
    void trendUptrendRequiresStrictMaOrdering() {
        CandidateFacts c = baseCandidate("A", "HOSE")
                .indicator(IndicatorCode.MA20, Map.of(IndicatorComponent.VALUE, new BigDecimal("30")))
                .indicator(IndicatorCode.MA50, Map.of(IndicatorComponent.VALUE, new BigDecimal("20")))
                .indicator(IndicatorCode.MA200, Map.of(IndicatorComponent.VALUE, new BigDecimal("10")))
                .build();
        var trend = ScreenerV1.deriveTrend(c);
        assertThat(trend.unavailable()).isFalse();
        assertThat(trend.direction()).isEqualTo(TrendDirection.UPTREND);
    }

    @Test
    void trendWithTiedMovingAveragesIsSideways() {
        CandidateFacts c = baseCandidate("A", "HOSE")
                .indicator(IndicatorCode.MA20, Map.of(IndicatorComponent.VALUE, new BigDecimal("20")))
                .indicator(IndicatorCode.MA50, Map.of(IndicatorComponent.VALUE, new BigDecimal("20")))
                .indicator(IndicatorCode.MA200, Map.of(IndicatorComponent.VALUE, new BigDecimal("20")))
                .build();
        var trend = ScreenerV1.deriveTrend(c);
        assertThat(trend.unavailable()).isFalse();
        assertThat(trend.direction()).isEqualTo(TrendDirection.SIDEWAYS);
    }

    @Test
    void trendWithMa200UnavailableIsExcludedWithInsufficientHistory() {
        CandidateFacts c = baseCandidate("A", "HOSE")
                .indicator(IndicatorCode.MA20, Map.of(IndicatorComponent.VALUE, new BigDecimal("30")))
                .indicator(IndicatorCode.MA50, Map.of(IndicatorComponent.VALUE, new BigDecimal("20")))
                .build(); // no MA200 entry at all
        var trend = ScreenerV1.deriveTrend(c);
        assertThat(trend.unavailable()).isTrue();
    }

    // ── S-4: fundamental/valuation exclusion reasons ────────────────────────

    @Test
    void revenueGrowthFilterExcludesMissingMetricWithReason() {
        CandidateFacts c = baseCandidate("A", "HOSE").build(); // no fundamentalMetrics entries
        ScreenCriteria criteria = criteria(null, null, null,
                new FundamentalFilter(BigDecimal.ZERO, new BigDecimal("999"), null, null, null, null, null, null,
                        null, null, null, null, null, null));

        CandidateResult result = ScreenerV1.evaluate(c, criteria);
        assertThat(onlyOutcome(result).status()).isEqualTo(CategoryStatus.UNAVAILABLE);
        assertThat(onlyOutcome(result).reasonCode()).isEqualTo("MISSING");
    }

    @Test
    void peFilterExcludesWithheldValuationEntirely() {
        CandidateFacts c = baseCandidate("A", "HOSE").valuationPublished(false).build();
        ScreenCriteria criteria = criteria(null, null, null,
                new FundamentalFilter(null, null, null, null, null, null, null, null, BigDecimal.ZERO,
                        new BigDecimal("999"), null, null, null, null));

        CandidateResult result = ScreenerV1.evaluate(c, criteria);
        assertThat(onlyOutcome(result).status()).isEqualTo(CategoryStatus.UNAVAILABLE);
        assertThat(onlyOutcome(result).reasonCode()).isEqualTo("VALUATION_WITHHELD");
    }

    @Test
    void peFilterExcludesNegativeEarningsNotApplicableDistinctFromMissing() {
        CandidateFacts c = baseCandidate("A", "HOSE").valuationPublished(true)
                .valuationMetric(ValuationMetricCode.PE, new MetricPoint(MetricApplicability.NOT_APPLICABLE, null,
                        "NEGATIVE_EARNINGS"))
                .build();
        ScreenCriteria criteria = criteria(null, null, null,
                new FundamentalFilter(null, null, null, null, null, null, null, null, BigDecimal.ZERO,
                        new BigDecimal("999"), null, null, null, null));

        CandidateResult result = ScreenerV1.evaluate(c, criteria);
        assertThat(onlyOutcome(result).status()).isEqualTo(CategoryStatus.UNAVAILABLE);
        assertThat(onlyOutcome(result).reasonCode()).isEqualTo("NEGATIVE_EARNINGS");
    }

    // ── Combination and reproducibility ──────────────────────────────────────

    @Test
    void threeCategoryIntersectionMatchesOnlyStocksSatisfyingEveryFilter() {
        CandidateFacts full = baseCandidate("A", "HOSE").latestClose("100")
                .indicator(IndicatorCode.RSI14, Map.of(IndicatorComponent.VALUE, new BigDecimal("70")))
                .fundamentalMetric("ROE", new MetricPoint(MetricApplicability.DEFINED, new BigDecimal("15"), null))
                .build();
        CandidateFacts failsRsi = baseCandidate("B", "HOSE").latestClose("100")
                .indicator(IndicatorCode.RSI14, Map.of(IndicatorComponent.VALUE, new BigDecimal("20")))
                .fundamentalMetric("ROE", new MetricPoint(MetricApplicability.DEFINED, new BigDecimal("15"), null))
                .build();
        ScreenCriteria criteria = criteria(
                new MarketFilter(Set_of("HOSE"), null, null, null),
                new PriceFilter(new BigDecimal("50"), new BigDecimal("150"), null, null),
                new TechnicalFilter(new BigDecimal("60"), new BigDecimal("100"), null, null, null, null, null, null,
                        null, null),
                new FundamentalFilter(null, null, null, null, new BigDecimal("10"), new BigDecimal("20"), null,
                        null, null, null, null, null, null, null));

        assertThat(ScreenerV1.evaluate(full, criteria).matched()).isTrue();
        assertThat(ScreenerV1.evaluate(failsRsi, criteria).matched()).isFalse();
    }

    @Test
    void noFiltersSelectedMatchesEveryCandidate() {
        CandidateFacts c = baseCandidate("A", "HOSE").build();
        ScreenCriteria criteria = criteria(null, null, null, null);
        assertThat(ScreenerV1.evaluate(c, criteria).matched()).isTrue();
    }

    @Test
    void contradictoryRangeIsRejectedBeforeAnyCandidateIsEvaluated() {
        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> ScreenerV1.validateRange(new BigDecimal("100"), new BigDecimal("50"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INVALID_FILTER_RANGE");
    }

    @Test
    void replayIsDeterministic() {
        CandidateFacts c = baseCandidate("A", "HOSE").latestClose("100").build();
        ScreenCriteria criteria = criteria(null, new PriceFilter(new BigDecimal("50"), new BigDecimal("150"), null,
                null), null, null);

        CandidateResult first = ScreenerV1.evaluate(c, criteria);
        CandidateResult second = ScreenerV1.evaluate(c, criteria);
        assertThat(first).isEqualTo(second);
    }

    // ── Fixtures and helpers ─────────────────────────────────────────────────

    private static List<DailyBarPoint> flatBars(int count, String price) {
        List<DailyBarPoint> bars = new ArrayList<>();
        LocalDate start = LocalDate.of(2026, 7, 1);
        for (int i = 0; i < count; i++) {
            bars.add(new DailyBarPoint(start.plusDays(i), new BigDecimal(price), new BigDecimal(price),
                    new BigDecimal(price)));
        }
        return bars;
    }

    private static CategoryOutcomeHolder onlyOutcome(CandidateResult result) {
        assertThat(result.categoryOutcomes()).hasSize(1);
        var outcome = result.categoryOutcomes().get(0);
        return new CategoryOutcomeHolder(outcome.status(), outcome.reasonCode());
    }

    private record CategoryOutcomeHolder(CategoryStatus status, String reasonCode) {
    }

    private static ScreenCriteria criteria(MarketFilter market, PriceFilter price, TechnicalFilter technical,
            FundamentalFilter fundamental) {
        return new ScreenCriteria(market, price, technical, fundamental);
    }

    private static <T> java.util.Set<T> Set_of(T... items) {
        return java.util.Set.of(items);
    }

    private static CandidateBuilder baseCandidate(String symbol, String exchange) {
        return new CandidateBuilder(symbol, exchange);
    }

    /** Test-only builder over the immutable {@link CandidateFacts} record. */
    private static final class CandidateBuilder {
        private final String symbol;
        private final String exchange;
        private UUID sectorId = SECTOR_A;
        private String sectorName = "Sector A";
        private Long sharesOutstanding = 1_000_000L;
        private BigDecimal latestClose = new BigDecimal("100");
        private BigDecimal previousValidClose = new BigDecimal("95");
        private Long latestVolume = 100_000L;
        private List<DailyBarPoint> recentBars = List.of();
        private final java.util.Map<IndicatorCode, IndicatorSnapshot> indicators = new java.util.HashMap<>();
        private final java.util.Map<String, MetricPoint> fundamentalMetrics = new java.util.HashMap<>();
        private boolean valuationPublished = true;
        private final java.util.Map<ValuationMetricCode, MetricPoint> valuationMetrics = new java.util.HashMap<>();

        CandidateBuilder(String symbol, String exchange) {
            this.symbol = symbol;
            this.exchange = exchange;
        }

        CandidateBuilder sectorId(UUID id, String name) {
            this.sectorId = id;
            this.sectorName = name;
            return this;
        }

        CandidateBuilder sharesOutstanding(Long shares) {
            this.sharesOutstanding = shares;
            return this;
        }

        CandidateBuilder latestClose(String value) {
            this.latestClose = value == null ? null : new BigDecimal(value);
            return this;
        }

        CandidateBuilder latestClose(BigDecimal value) {
            this.latestClose = value;
            return this;
        }

        CandidateBuilder previousValidClose(String value) {
            this.previousValidClose = value == null ? null : new BigDecimal(value);
            return this;
        }

        CandidateBuilder latestVolume(Long volume) {
            this.latestVolume = volume;
            return this;
        }

        CandidateBuilder recentBars(List<DailyBarPoint> bars) {
            this.recentBars = bars;
            return this;
        }

        CandidateBuilder indicator(IndicatorCode code, java.util.Map<IndicatorComponent, BigDecimal> components) {
            indicators.put(code, new IndicatorSnapshot(MetricApplicability.DEFINED, components, null));
            return this;
        }

        CandidateBuilder fundamentalMetric(String code, MetricPoint point) {
            fundamentalMetrics.put(code, point);
            return this;
        }

        CandidateBuilder valuationPublished(boolean published) {
            this.valuationPublished = published;
            return this;
        }

        CandidateBuilder valuationMetric(ValuationMetricCode code, MetricPoint point) {
            valuationMetrics.put(code, point);
            return this;
        }

        CandidateFacts build() {
            return new CandidateFacts(UUID.randomUUID(), symbol, symbol + " Corp", exchange, sectorId, sectorName,
                    sharesOutstanding, LocalDate.of(2026, 8, 19), DataStatus.CURRENT, latestClose,
                    previousValidClose, latestVolume, recentBars, java.util.Map.copyOf(indicators),
                    java.util.Map.copyOf(fundamentalMetrics), valuationPublished,
                    java.util.Map.copyOf(valuationMetrics));
        }
    }
}

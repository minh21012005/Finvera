package com.minhnb.finvera_be.stock.domain.screener;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.stock.domain.model.DecimalMath;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.IndicatorCode;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.IndicatorComponent;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.MetricApplicability;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.ValuationMetricCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * {@code screener-v1} (Feature 003 {@code contracts/screener-v1.md}): the
 * deterministic, versioned filter-evaluation engine behind the stock
 * screener. Pure and framework-free, matching the pattern
 * {@code technical-indicators-v1}/{@code valuation-v1} already established.
 *
 * <p>S-1: every filter reads only already-accepted facts/results supplied by
 * the caller in {@link CandidateFacts}; this class performs no I/O and no
 * recalculation of a technical/fundamental/valuation value. S-2: every
 * selected filter, across every category, combines with logical AND. S-4:
 * a candidate whose selected filter cannot be evaluated (an unavailable
 * upstream value) is excluded with a reason, distinct from a candidate that
 * was evaluated and simply did not satisfy the filter.
 */
public final class ScreenerV1 {

    public static final String RULE_VERSION = "screener-v1";

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final int PRICE_DISPLAY_SCALE = 6;
    private static final int BREAKOUT_LOOKBACK_SESSIONS = 20;

    private ScreenerV1() {
    }

    // ── Public evaluation entry point ───────────────────────────────────────

    public static CandidateResult evaluate(CandidateFacts candidate, ScreenCriteria criteria) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(criteria, "criteria");

        List<CategoryOutcome> outcomes = new ArrayList<>();
        Map<String, String> matchedValues = new LinkedHashMap<>();

        if (criteria.market() != null) {
            outcomes.add(evaluateMarket(candidate, criteria.market(), matchedValues));
        }
        if (criteria.price() != null) {
            outcomes.add(evaluatePrice(candidate, criteria.price(), matchedValues));
        }
        if (criteria.technical() != null) {
            outcomes.add(evaluateTechnical(candidate, criteria.technical(), matchedValues));
        }
        if (criteria.fundamental() != null) {
            outcomes.add(evaluateFundamental(candidate, criteria.fundamental(), matchedValues));
        }

        boolean overallMatch = outcomes.stream().allMatch(o -> o.status() == CategoryStatus.MATCHED);
        return new CandidateResult(candidate.instrumentId(), overallMatch,
                overallMatch ? Map.copyOf(matchedValues) : Map.of(), List.copyOf(outcomes));
    }

    // ── Market ───────────────────────────────────────────────────────────────

    private static CategoryOutcome evaluateMarket(CandidateFacts c, MarketFilter f, Map<String, String> values) {
        if (f.exchange() != null && !f.exchange().isEmpty()) {
            if (!f.exchange().contains(c.exchange())) {
                return notMatched("MARKET");
            }
            values.put("exchange", c.exchange());
        }
        if (f.sectorId() != null && !f.sectorId().isEmpty()) {
            if (c.sectorId() == null) {
                return unavailable("MARKET", "SECTOR_UNCLASSIFIED");
            }
            if (!f.sectorId().contains(c.sectorId())) {
                return notMatched("MARKET");
            }
            values.put("sector", c.sectorName());
        }
        if (f.marketCapMin() != null || f.marketCapMax() != null) {
            if (c.sharesOutstanding() == null) {
                return unavailable("MARKET", "SHARES_OUTSTANDING_MISSING");
            }
            if (c.latestClose() == null) {
                return unavailable("MARKET", "PRICE_UNAVAILABLE");
            }
            BigDecimal marketCap = c.latestClose()
                    .multiply(BigDecimal.valueOf(c.sharesOutstanding()))
                    .setScale(PRICE_DISPLAY_SCALE, RoundingMode.UNNECESSARY);
            if (!withinRange(marketCap, f.marketCapMin(), f.marketCapMax())) {
                return notMatched("MARKET");
            }
            values.put("marketCap", marketCap.toPlainString());
        }
        return matched("MARKET");
    }

    // ── Price ────────────────────────────────────────────────────────────────

    private static CategoryOutcome evaluatePrice(CandidateFacts c, PriceFilter f, Map<String, String> values) {
        if (f.priceMin() != null || f.priceMax() != null) {
            if (c.latestClose() == null) {
                return unavailable("PRICE", "PRICE_UNAVAILABLE");
            }
            if (!withinRange(c.latestClose(), f.priceMin(), f.priceMax())) {
                return notMatched("PRICE");
            }
            values.put("price", c.latestClose().toPlainString());
        }
        if (f.priceChangePercentMin() != null || f.priceChangePercentMax() != null) {
            if (c.latestClose() == null) {
                return unavailable("PRICE", "PRICE_UNAVAILABLE");
            }
            if (c.previousValidClose() == null) {
                return unavailable("PRICE", "REFERENCE_PRICE_UNAVAILABLE");
            }
            if (c.previousValidClose().signum() == 0) {
                return unavailable("PRICE", "REFERENCE_PRICE_INVALID");
            }
            BigDecimal changePercent = c.latestClose().subtract(c.previousValidClose())
                    .multiply(ONE_HUNDRED)
                    .divide(c.previousValidClose(), PRICE_DISPLAY_SCALE, RoundingMode.HALF_UP);
            if (!withinRange(changePercent, f.priceChangePercentMin(), f.priceChangePercentMax())) {
                return notMatched("PRICE");
            }
            values.put("priceChangePercent", changePercent.toPlainString());
        }
        return matched("PRICE");
    }

    // ── Technical ────────────────────────────────────────────────────────────

    private static CategoryOutcome evaluateTechnical(CandidateFacts c, TechnicalFilter f, Map<String, String> values) {
        if (f.rsiMin() != null || f.rsiMax() != null) {
            BigDecimal rsi = componentValue(c, IndicatorCode.RSI14, IndicatorComponent.VALUE);
            if (rsi == null) {
                return unavailable("TECHNICAL", indicatorReason(c, IndicatorCode.RSI14));
            }
            if (!withinRange(rsi, f.rsiMin(), f.rsiMax())) {
                return notMatched("TECHNICAL");
            }
            values.put("rsi", rsi.toPlainString());
        }
        if (f.macdSignal() != null) {
            BigDecimal histogram = componentValue(c, IndicatorCode.MACD, IndicatorComponent.HISTOGRAM);
            if (histogram == null) {
                return unavailable("TECHNICAL", indicatorReason(c, IndicatorCode.MACD));
            }
            MacdSignal actual = histogram.signum() > 0 ? MacdSignal.BULLISH
                    : histogram.signum() < 0 ? MacdSignal.BEARISH
                    : MacdSignal.NEUTRAL;
            if (actual != f.macdSignal()) {
                return notMatched("TECHNICAL");
            }
            values.put("macdSignal", actual.name());
        }
        if (f.maRelationship() != null && !f.maRelationship().isEmpty()) {
            for (MaRelationship relationship : f.maRelationship()) {
                CategoryOutcome outcome = evaluateMaRelationship(c, relationship, values);
                if (outcome.status() != CategoryStatus.MATCHED) {
                    return outcome;
                }
            }
        }
        if (f.volumeMin() != null || f.volumeMax() != null) {
            if (c.latestVolume() == null) {
                return unavailable("TECHNICAL", "VOLUME_UNAVAILABLE");
            }
            if (!withinRange(c.latestVolume(), f.volumeMin(), f.volumeMax())) {
                return notMatched("TECHNICAL");
            }
            values.put("volume", String.valueOf(c.latestVolume()));
        }
        if (f.relativeVolumeMin() != null || f.relativeVolumeMax() != null) {
            BigDecimal relativeVolume = componentValue(c, IndicatorCode.RELATIVE_VOLUME, IndicatorComponent.VALUE);
            if (relativeVolume == null) {
                return unavailable("TECHNICAL", indicatorReason(c, IndicatorCode.RELATIVE_VOLUME));
            }
            if (!withinRange(relativeVolume, f.relativeVolumeMin(), f.relativeVolumeMax())) {
                return notMatched("TECHNICAL");
            }
            values.put("relativeVolume", relativeVolume.toPlainString());
        }
        if (f.breakout() != null) {
            BreakoutResult breakout = deriveBreakout(c);
            if (breakout.unavailable()) {
                return unavailable("TECHNICAL", "INSUFFICIENT_HISTORY");
            }
            if (breakout.condition() != f.breakout()) {
                return notMatched("TECHNICAL");
            }
            values.put("breakout", breakout.condition().name());
        }
        if (f.trend() != null) {
            TrendResult trend = deriveTrend(c);
            if (trend.unavailable()) {
                return unavailable("TECHNICAL", "INSUFFICIENT_HISTORY");
            }
            if (trend.direction() != f.trend()) {
                return notMatched("TECHNICAL");
            }
            values.put("trend", trend.direction().name());
        }
        return matched("TECHNICAL");
    }

    private static CategoryOutcome evaluateMaRelationship(CandidateFacts c, MaRelationship relationship,
            Map<String, String> values) {
        BigDecimal left = maRelationshipOperand(c, relationship, true);
        BigDecimal right = maRelationshipOperand(c, relationship, false);
        if (left == null || right == null) {
            return unavailable("TECHNICAL", "INSUFFICIENT_HISTORY");
        }
        boolean above = relationship.name().contains("_ABOVE_");
        boolean satisfied = above ? left.compareTo(right) > 0 : left.compareTo(right) < 0;
        if (!satisfied) {
            return notMatched("TECHNICAL");
        }
        values.put("maRelationship", relationship.name());
        return matched("TECHNICAL");
    }

    private static BigDecimal maRelationshipOperand(CandidateFacts c, MaRelationship relationship, boolean leftSide) {
        String name = relationship.name();
        String[] sides = name.replace("_ABOVE_", "|").replace("_BELOW_", "|").split("\\|");
        String token = leftSide ? sides[0] : sides[1];
        return switch (token) {
            case "PRICE" -> c.latestClose();
            case "MA20" -> componentValue(c, IndicatorCode.MA20, IndicatorComponent.VALUE);
            case "MA50" -> componentValue(c, IndicatorCode.MA50, IndicatorComponent.VALUE);
            case "MA200" -> componentValue(c, IndicatorCode.MA200, IndicatorComponent.VALUE);
            default -> throw new IllegalArgumentException("Unknown MA relationship operand: " + token);
        };
    }

    // ── Fundamental ──────────────────────────────────────────────────────────

    private static CategoryOutcome evaluateFundamental(CandidateFacts c, FundamentalFilter f, Map<String, String> values) {
        CategoryOutcome outcome;

        outcome = evaluateSummaryMetric(c, "REVENUE_GROWTH_PERCENT", f.revenueGrowthPercentMin(),
                f.revenueGrowthPercentMax(), "revenueGrowthPercent", values);
        if (outcome != null) {
            return outcome;
        }
        outcome = evaluateSummaryMetric(c, "EPS_GROWTH_PERCENT", f.earningsGrowthPercentMin(),
                f.earningsGrowthPercentMax(), "earningsGrowthPercent", values);
        if (outcome != null) {
            return outcome;
        }
        outcome = evaluateSummaryMetric(c, "ROE", f.roeMin(), f.roeMax(), "roe", values);
        if (outcome != null) {
            return outcome;
        }
        outcome = evaluateSummaryMetric(c, "ROA", f.roaMin(), f.roaMax(), "roa", values);
        if (outcome != null) {
            return outcome;
        }
        outcome = evaluateSummaryMetric(c, "DEBT_TO_EQUITY", f.debtToEquityMin(), f.debtToEquityMax(),
                "debtToEquity", values);
        if (outcome != null) {
            return outcome;
        }
        outcome = evaluateValuationMetric(c, ValuationMetricCode.PE, f.peMin(), f.peMax(), "pe", values);
        if (outcome != null) {
            return outcome;
        }
        outcome = evaluateValuationMetric(c, ValuationMetricCode.PB, f.pbMin(), f.pbMax(), "pb", values);
        if (outcome != null) {
            return outcome;
        }
        return matched("FUNDAMENTAL");
    }

    /** Returns {@code null} when the filter was not selected (min and max both absent). */
    private static CategoryOutcome evaluateSummaryMetric(CandidateFacts c, String metricCode, BigDecimal min,
            BigDecimal max, String outputKey, Map<String, String> values) {
        if (min == null && max == null) {
            return null;
        }
        MetricPoint point = c.fundamentalMetrics().get(metricCode);
        if (point == null || point.applicability() != MetricApplicability.DEFINED || point.value() == null) {
            String reason = point == null ? "MISSING" : reasonOrDefault(point, "MISSING");
            return unavailable("FUNDAMENTAL", reason);
        }
        if (!withinRange(point.value(), min, max)) {
            return notMatched("FUNDAMENTAL");
        }
        values.put(outputKey, point.value().toPlainString());
        return null;
    }

    /** Returns {@code null} when the filter was not selected (min and max both absent). */
    private static CategoryOutcome evaluateValuationMetric(CandidateFacts c, ValuationMetricCode metricCode,
            BigDecimal min, BigDecimal max, String outputKey, Map<String, String> values) {
        if (min == null && max == null) {
            return null;
        }
        if (!c.valuationPublished()) {
            return unavailable("FUNDAMENTAL", "VALUATION_WITHHELD");
        }
        MetricPoint point = c.valuationMetrics().get(metricCode);
        if (point == null || point.applicability() != MetricApplicability.DEFINED || point.value() == null) {
            String reason = point == null ? "MISSING" : reasonOrDefault(point, "NOT_APPLICABLE");
            return unavailable("FUNDAMENTAL", reason);
        }
        if (!withinRange(point.value(), min, max)) {
            return notMatched("FUNDAMENTAL");
        }
        values.put(outputKey, point.value().toPlainString());
        return null;
    }

    private static String reasonOrDefault(MetricPoint point, String fallback) {
        return point.qualityReason() != null ? point.qualityReason() : fallback;
    }

    // ── Breakout and Trend (new in screener-v1; contracts/screener-v1.md) ──────

    static BreakoutResult deriveBreakout(CandidateFacts c) {
        List<DailyBarPoint> bars = c.recentBars();
        if (bars == null || bars.size() < BREAKOUT_LOOKBACK_SESSIONS + 1) {
            return new BreakoutResult(true, null);
        }
        List<DailyBarPoint> ordered = bars.stream()
                .sorted((a, b) -> a.tradingDate().compareTo(b.tradingDate()))
                .toList();
        int n = ordered.size() - 1;
        DailyBarPoint asOf = ordered.get(n);
        List<DailyBarPoint> priorWindow = ordered.subList(n - BREAKOUT_LOOKBACK_SESSIONS, n);
        BigDecimal priorHigh = priorWindow.stream().map(DailyBarPoint::high).max(BigDecimal::compareTo).orElseThrow();
        BigDecimal priorLow = priorWindow.stream().map(DailyBarPoint::low).min(BigDecimal::compareTo).orElseThrow();
        BreakoutCondition condition;
        if (asOf.close().compareTo(priorHigh) > 0) {
            condition = BreakoutCondition.BREAKOUT_UP;
        } else if (asOf.close().compareTo(priorLow) < 0) {
            condition = BreakoutCondition.BREAKOUT_DOWN;
        } else {
            condition = BreakoutCondition.NONE;
        }
        return new BreakoutResult(false, condition);
    }

    static TrendResult deriveTrend(CandidateFacts c) {
        BigDecimal ma20 = componentValue(c, IndicatorCode.MA20, IndicatorComponent.VALUE);
        BigDecimal ma50 = componentValue(c, IndicatorCode.MA50, IndicatorComponent.VALUE);
        BigDecimal ma200 = componentValue(c, IndicatorCode.MA200, IndicatorComponent.VALUE);
        if (ma20 == null || ma50 == null || ma200 == null) {
            return new TrendResult(true, null);
        }
        TrendDirection direction;
        if (ma20.compareTo(ma50) > 0 && ma50.compareTo(ma200) > 0) {
            direction = TrendDirection.UPTREND;
        } else if (ma20.compareTo(ma50) < 0 && ma50.compareTo(ma200) < 0) {
            direction = TrendDirection.DOWNTREND;
        } else {
            direction = TrendDirection.SIDEWAYS;
        }
        return new TrendResult(false, direction);
    }

    // ── Shared helpers ───────────────────────────────────────────────────────

    private static BigDecimal componentValue(CandidateFacts c, IndicatorCode code, IndicatorComponent component) {
        IndicatorSnapshot snapshot = c.technicalIndicators().get(code);
        if (snapshot == null || snapshot.applicability() != MetricApplicability.DEFINED) {
            return null;
        }
        return snapshot.components().get(component);
    }

    private static String indicatorReason(CandidateFacts c, IndicatorCode code) {
        IndicatorSnapshot snapshot = c.technicalIndicators().get(code);
        if (snapshot != null && snapshot.qualityReason() != null) {
            return snapshot.qualityReason();
        }
        return "INSUFFICIENT_HISTORY";
    }

    private static boolean withinRange(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (min != null && value.compareTo(min) < 0) {
            return false;
        }
        return max == null || value.compareTo(max) <= 0;
    }

    private static boolean withinRange(long value, Long min, Long max) {
        if (min != null && value < min) {
            return false;
        }
        return max == null || value <= max;
    }

    private static CategoryOutcome matched(String category) {
        return new CategoryOutcome(category, CategoryStatus.MATCHED, null);
    }

    private static CategoryOutcome notMatched(String category) {
        return new CategoryOutcome(category, CategoryStatus.NOT_MATCHED, null);
    }

    private static CategoryOutcome unavailable(String category, String reasonCode) {
        return new CategoryOutcome(category, CategoryStatus.UNAVAILABLE, reasonCode);
    }

    /** Validates a request-level range before any candidate is evaluated (research R-010). */
    public static void validateRange(BigDecimal min, BigDecimal max) {
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new IllegalArgumentException("INVALID_FILTER_RANGE");
        }
    }

    // ── Enums ────────────────────────────────────────────────────────────────

    public enum MacdSignal { BULLISH, BEARISH, NEUTRAL }

    public enum MaRelationship {
        PRICE_ABOVE_MA20, PRICE_BELOW_MA20,
        PRICE_ABOVE_MA50, PRICE_BELOW_MA50,
        PRICE_ABOVE_MA200, PRICE_BELOW_MA200,
        MA20_ABOVE_MA50, MA20_BELOW_MA50,
        MA50_ABOVE_MA200, MA50_BELOW_MA200
    }

    public enum BreakoutCondition { BREAKOUT_UP, BREAKOUT_DOWN, NONE }

    public enum TrendDirection { UPTREND, DOWNTREND, SIDEWAYS }

    public enum CategoryStatus { MATCHED, NOT_MATCHED, UNAVAILABLE }

    // ── Value objects ────────────────────────────────────────────────────────

    public record DailyBarPoint(LocalDate tradingDate, BigDecimal close, BigDecimal high, BigDecimal low) {
    }

    public record IndicatorSnapshot(
            MetricApplicability applicability,
            Map<IndicatorComponent, BigDecimal> components,
            String qualityReason) {
    }

    public record MetricPoint(MetricApplicability applicability, BigDecimal value, String qualityReason) {
    }

    public record CandidateFacts(
            UUID instrumentId,
            String symbol,
            String companyName,
            String exchange,
            UUID sectorId,
            String sectorName,
            Long sharesOutstanding,
            LocalDate asOfTradingDate,
            DataStatus priceDataStatus,
            BigDecimal latestClose,
            BigDecimal previousValidClose,
            Long latestVolume,
            List<DailyBarPoint> recentBars,
            Map<IndicatorCode, IndicatorSnapshot> technicalIndicators,
            Map<String, MetricPoint> fundamentalMetrics,
            boolean valuationPublished,
            Map<ValuationMetricCode, MetricPoint> valuationMetrics) {
    }

    public record MarketFilter(Set<String> exchange, Set<UUID> sectorId, BigDecimal marketCapMin,
            BigDecimal marketCapMax) {
    }

    public record PriceFilter(BigDecimal priceMin, BigDecimal priceMax, BigDecimal priceChangePercentMin,
            BigDecimal priceChangePercentMax) {
    }

    public record TechnicalFilter(
            BigDecimal rsiMin, BigDecimal rsiMax,
            MacdSignal macdSignal,
            Set<MaRelationship> maRelationship,
            Long volumeMin, Long volumeMax,
            BigDecimal relativeVolumeMin, BigDecimal relativeVolumeMax,
            BreakoutCondition breakout,
            TrendDirection trend) {
    }

    public record FundamentalFilter(
            BigDecimal revenueGrowthPercentMin, BigDecimal revenueGrowthPercentMax,
            BigDecimal earningsGrowthPercentMin, BigDecimal earningsGrowthPercentMax,
            BigDecimal roeMin, BigDecimal roeMax,
            BigDecimal roaMin, BigDecimal roaMax,
            BigDecimal peMin, BigDecimal peMax,
            BigDecimal pbMin, BigDecimal pbMax,
            BigDecimal debtToEquityMin, BigDecimal debtToEquityMax) {
    }

    public record ScreenCriteria(MarketFilter market, PriceFilter price, TechnicalFilter technical,
            FundamentalFilter fundamental) {
    }

    public record CategoryOutcome(String category, CategoryStatus status, String reasonCode) {
    }

    public record CandidateResult(UUID instrumentId, boolean matched, Map<String, String> matchedValues,
            List<CategoryOutcome> categoryOutcomes) {
    }

    record BreakoutResult(boolean unavailable, BreakoutCondition condition) {
    }

    record TrendResult(boolean unavailable, TrendDirection direction) {
    }
}

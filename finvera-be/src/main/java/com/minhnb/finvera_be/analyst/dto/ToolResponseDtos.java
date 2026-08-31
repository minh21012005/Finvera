package com.minhnb.finvera_be.analyst.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ToolResponseDtos {

    private ToolResponseDtos() {
    }

    public record MarketOverviewToolResponse(
            String vnIndexValue,
            String vnIndexChangePercent,
            int advancers,
            int decliners,
            int unchanged,
            Instant asOf,
            Map<String, Object> raw) {
    }

    /** T049: price/changePercent are null (never "0") when the price is not DEFINED; dataStatus says why. */
    public record StockSummaryToolResponse(
            String symbol,
            String companyName,
            String price,
            String changePercent,
            Long volume,
            Instant asOf,
            Map<String, Object> raw,
            String dataStatus,
            List<String> reasonCodes) {
        public StockSummaryToolResponse(String symbol, String companyName, String price, String changePercent,
                Long volume, Instant asOf, Map<String, Object> raw) {
            this(symbol, companyName, price, changePercent, volume, asOf, raw, "CURRENT", List.of());
        }
    }

    /** T049: one entry per triggered strategy, with its levels and risk — not an arbitrary "first" signal. */
    public record TechnicalSignalDto(
            String direction,
            List<EvidenceFactorDto> evidenceFactors,
            String strategyCode,
            String entryLow,
            String entryHigh,
            String stopLoss,
            String target1,
            String target2,
            String riskReward,
            Integer riskScore,
            String riskLevel,
            String signalStrength,
            List<EvidenceFactorDto> riskFactors) {
        public TechnicalSignalDto(String direction, List<EvidenceFactorDto> evidenceFactors) {
            this(direction, evidenceFactors, null, null, null, null, null, null, null, null, null, null, List.of());
        }
    }

    public record TechnicalToolResponse(
            String symbol,
            Map<String, Object> indicators,
            TechnicalSignalDto signal,
            List<EvidenceFactorDto> riskFactors,
            Instant asOf,
            List<TechnicalSignalDto> signals,
            String dataStatus) {
        public TechnicalToolResponse(String symbol, Map<String, Object> indicators, TechnicalSignalDto signal,
                List<EvidenceFactorDto> riskFactors, Instant asOf) {
            this(symbol, indicators, signal, riskFactors, asOf, signal == null ? List.of() : List.of(signal), "CURRENT");
        }
    }

    /** T049: a fundamental/valuation metric with its applicability and the engine's own quality reason. */
    public record MetricFactDto(
            String metricCode,
            String value,
            String applicability,
            String qualityReason,
            String ownHistoryPercentile,
            String sectorPercentile,
            String effectiveWeight) {
    }

    public record FundamentalsToolResponse(
            String symbol,
            String eps,
            String roe,
            String revenueGrowthPercent,
            String period,
            Instant asOf,
            Map<String, Object> raw,
            String epsTtm,
            String epsGrowthPercent,
            List<MetricFactDto> metrics,
            String dataStatus,
            List<String> reasonCodes) {
        public FundamentalsToolResponse(String symbol, String eps, String roe, String revenueGrowthPercent,
                String period, Instant asOf, Map<String, Object> raw) {
            this(symbol, eps, roe, revenueGrowthPercent, period, asOf, raw, null, null, List.of(), "UNAVAILABLE", List.of());
        }
    }

    /**
     * T049: {@code classification} is null whenever the engine withheld the assessment
     * ({@code published=false}); it is never defaulted to a label. Score, confidence, bases,
     * per-metric percentiles/weights and the engine's reason codes travel with it so the
     * model can explain the result instead of guessing.
     */
    public record ValuationToolResponse(
            String symbol,
            String peRatio,
            String pbRatio,
            String classification,
            String comparisonBasis,
            Instant asOf,
            Map<String, Object> raw,
            boolean published,
            String ruleVersion,
            String score,
            Integer displayedScore,
            Integer confidence,
            Integer historyPointCount,
            Integer sectorConstituentCount,
            List<MetricFactDto> metrics,
            List<String> reasonCodes,
            String dataStatus,
            String priceTradingDate,
            Map<String, String> inputBasis) {
        public ValuationToolResponse(String symbol, String peRatio, String pbRatio, String classification,
                String comparisonBasis, Instant asOf, Map<String, Object> raw) {
            this(symbol, peRatio, pbRatio, classification, comparisonBasis, asOf, raw, classification != null,
                    null, null, null, null, null, null, List.of(), List.of(), "UNAVAILABLE", null, Map.of());
        }
    }

    public record PositionItemDto(
            String symbol,
            String quantity,
            String marketValue,
            String unrealizedPnlPercent) {
    }

    public record PortfolioPositionsToolResponse(
            List<PositionItemDto> positions,
            Instant asOf) {
    }

    public record PortfolioAnalyticsToolResponse(
            String totalValue,
            String totalUnrealizedPnlPercent,
            Instant asOf,
            Map<String, Object> raw) {
    }

    public record NewsArticleItemDto(
            UUID id,
            String title,
            String source,
            Instant publishedAt,
            String category,
            String sentiment) {
    }

    public record NewsBrowseToolResponse(
            List<NewsArticleItemDto> articles,
            Instant asOf) {
    }

    public record ScreenerExecutionToolResponse(
            List<Map<String, Object>> matches,
            int totalMatches,
            Instant asOf) {
    }
}

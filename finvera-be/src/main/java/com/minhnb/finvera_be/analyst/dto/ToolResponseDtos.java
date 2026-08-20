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

    public record StockSummaryToolResponse(
            String symbol,
            String companyName,
            String price,
            String changePercent,
            Long volume,
            Instant asOf,
            Map<String, Object> raw) {
    }

    public record TechnicalSignalDto(
            String direction,
            List<EvidenceFactorDto> evidenceFactors) {
    }

    public record TechnicalToolResponse(
            String symbol,
            Map<String, Object> indicators,
            TechnicalSignalDto signal,
            List<EvidenceFactorDto> riskFactors,
            Instant asOf) {
    }

    public record FundamentalsToolResponse(
            String symbol,
            String eps,
            String roe,
            String revenueGrowthPercent,
            String period,
            Instant asOf,
            Map<String, Object> raw) {
    }

    public record ValuationToolResponse(
            String symbol,
            String peRatio,
            String pbRatio,
            String classification,
            String comparisonBasis,
            Instant asOf,
            Map<String, Object> raw) {
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

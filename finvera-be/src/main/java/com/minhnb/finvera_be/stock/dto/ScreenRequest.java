package com.minhnb.finvera_be.stock.dto;

import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.BreakoutCondition;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.MacdSignal;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.MaRelationship;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.TrendDirection;
import com.minhnb.finvera_be.stock.service.screener.ScreenerService.SortDirection;
import com.minhnb.finvera_be.stock.service.screener.ScreenerService.SortField;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Version 1.0 transport DTO for `POST /screener/executions`
 * (contracts/stock-screener.openapi.yaml). Every property is optional; an
 * empty request selects the full candidate universe (research R-007, R-009).
 */
public record ScreenRequest(
        MarketFilter market,
        PriceFilter price,
        TechnicalFilter technical,
        FundamentalFilter fundamental,
        SortField sortField,
        SortDirection sortDirection,
        Integer limit,
        Integer offset) {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    public ScreenerV1.ScreenCriteria toCriteria() {
        return new ScreenerV1.ScreenCriteria(
                market == null ? null : market.toDomain(),
                price == null ? null : price.toDomain(),
                technical == null ? null : technical.toDomain(),
                fundamental == null ? null : fundamental.toDomain());
    }

    public SortField effectiveSortField() {
        return sortField == null ? SortField.SYMBOL : sortField;
    }

    public SortDirection effectiveSortDirection() {
        return sortDirection == null ? SortDirection.ASC : sortDirection;
    }

    public int effectiveLimit() {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.min(Math.max(limit, 1), MAX_LIMIT);
    }

    public int effectiveOffset() {
        return offset == null ? 0 : Math.max(offset, 0);
    }

    public record MarketFilter(
            List<String> exchange,
            List<UUID> sectorId,
            String marketCapMin,
            String marketCapMax) {
        ScreenerV1.MarketFilter toDomain() {
            return new ScreenerV1.MarketFilter(
                    exchange == null || exchange.isEmpty() ? null : Set.copyOf(exchange),
                    sectorId == null || sectorId.isEmpty() ? null : Set.copyOf(sectorId),
                    decimal(marketCapMin), decimal(marketCapMax));
        }
    }

    public record PriceFilter(
            String priceMin,
            String priceMax,
            String priceChangePercentMin,
            String priceChangePercentMax) {
        ScreenerV1.PriceFilter toDomain() {
            return new ScreenerV1.PriceFilter(
                    decimal(priceMin), decimal(priceMax),
                    decimal(priceChangePercentMin), decimal(priceChangePercentMax));
        }
    }

    public record TechnicalFilter(
            String rsiMin,
            String rsiMax,
            MacdSignal macdSignal,
            List<MaRelationship> maRelationship,
            Long volumeMin,
            Long volumeMax,
            String relativeVolumeMin,
            String relativeVolumeMax,
            BreakoutCondition breakout,
            TrendDirection trend) {
        ScreenerV1.TechnicalFilter toDomain() {
            return new ScreenerV1.TechnicalFilter(
                    decimal(rsiMin), decimal(rsiMax), macdSignal,
                    maRelationship == null || maRelationship.isEmpty() ? null : Set.copyOf(maRelationship),
                    volumeMin, volumeMax, decimal(relativeVolumeMin), decimal(relativeVolumeMax),
                    breakout, trend);
        }
    }

    public record FundamentalFilter(
            String revenueGrowthPercentMin,
            String revenueGrowthPercentMax,
            String earningsGrowthPercentMin,
            String earningsGrowthPercentMax,
            String roeMin,
            String roeMax,
            String roaMin,
            String roaMax,
            String peMin,
            String peMax,
            String pbMin,
            String pbMax,
            String debtToEquityMin,
            String debtToEquityMax) {
        ScreenerV1.FundamentalFilter toDomain() {
            return new ScreenerV1.FundamentalFilter(
                    decimal(revenueGrowthPercentMin), decimal(revenueGrowthPercentMax),
                    decimal(earningsGrowthPercentMin), decimal(earningsGrowthPercentMax),
                    decimal(roeMin), decimal(roeMax),
                    decimal(roaMin), decimal(roaMax),
                    decimal(peMin), decimal(peMax),
                    decimal(pbMin), decimal(pbMax),
                    decimal(debtToEquityMin), decimal(debtToEquityMax));
        }
    }

    private static BigDecimal decimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("INVALID_FILTER_RANGE");
        }
    }
}

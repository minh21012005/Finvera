package com.minhnb.finvera_be.stock.dto;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.Direction;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.SessionState;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.MetricApplicability;
import com.minhnb.finvera_be.stock.service.StockOverviewService.StockOverview;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Version 1.0 transport DTO for `GET /stocks/{symbol}` (contracts/stock-detail.openapi.yaml). */
public record StockOverviewResponse(
        SectionMeta meta,
        ProfileResponse profile,
        PriceResponse price,
        SessionResponse session) {

    public static StockOverviewResponse from(StockOverview overview) {
        return new StockOverviewResponse(
                SectionMeta.of(overview.symbol(), overview.asOf(), overview.tradingDate(), overview.dataStatus(),
                        overview.coherenceKey(), List.of("FINVERA_ACCEPTED"), overview.reasonCodes()),
                new ProfileResponse(overview.symbol(), overview.companyNameVi(), overview.companyNameEn(),
                        overview.venue(), overview.sector(), overview.sectorScheme(),
                        overview.listingStatus() == null ? "UNKNOWN" : overview.listingStatus(),
                        overview.sharesOutstanding()),
                PriceResponse.from(overview.price()),
                new SessionResponse(overview.sessionState(), overview.tradingDate(), "finvera-calendar-v1"));
    }

    public record ProfileResponse(
            String symbol,
            String companyName,
            String companyNameEn,
            String exchange,
            String sector,
            String sectorScheme,
            String listingStatus,
            Long sharesOutstanding) {
    }

    public record PriceResponse(
            String currency,
            String last,
            String referencePrice,
            String absoluteChange,
            String percentageChange,
            Direction direction,
            Long volume,
            String valueVnd,
            String marketCapVnd,
            MetricApplicability applicability,
            String changeBasisReason) {

        static PriceResponse from(com.minhnb.finvera_be.stock.domain.overview.StockOverviewCalculator.StockOverviewResult price) {
            return new PriceResponse("VND", decimal(price.lastPrice()), decimal(price.referencePrice()),
                    decimal(price.absoluteChange()), decimal(price.percentageChange()), price.direction(),
                    price.volume(), decimal(price.valueVnd()), decimal(price.marketCapVnd()),
                    price.priceApplicability(), price.changeBasisReason());
        }
    }

    public record SessionResponse(SessionState state, LocalDate tradingDate, String calendarVersion) {
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}

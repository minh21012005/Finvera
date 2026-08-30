package com.minhnb.finvera_be.stock.dto;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.Direction;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.SessionState;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.MetricApplicability;
import com.minhnb.finvera_be.stock.service.StockOverviewService;
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
                PriceResponse.from(overview.price(), overview.limits()),
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
            String openPrice,
            String highPrice,
            String lowPrice,
            String absoluteChange,
            String percentageChange,
            Direction direction,
            Long volume,
            String valueVnd,
            String marketCapVnd,
            MetricApplicability applicability,
            String changeBasisReason,
            String ceilingPrice,
            String floorPrice,
            Long foreignRoom,
            String limitState) {

        public static PriceResponse from(com.minhnb.finvera_be.stock.domain.overview.StockOverviewCalculator.StockOverviewResult price,
                StockOverviewService.SessionLimits limits) {
            StockOverviewService.SessionLimits l = limits == null ? StockOverviewService.SessionLimits.UNAVAILABLE : limits;
            return new PriceResponse("VND", decimal(price.lastPrice()), decimal(price.referencePrice()),
                    decimal(price.openPrice()), decimal(price.highPrice()), decimal(price.lowPrice()),
                    decimal(price.absoluteChange()), decimal(price.percentageChange()), price.direction(),
                    price.volume(), decimal(price.valueVnd()), decimal(price.marketCapVnd()),
                    price.priceApplicability(), price.changeBasisReason(),
                    decimal(l.ceilingPrice()), decimal(l.floorPrice()), l.foreignRoom(), l.limitState());
        }

        public PriceResponse(
                String currency, String last, String referencePrice, String openPrice, String highPrice,
                String lowPrice, String absoluteChange, String percentageChange, Direction direction,
                Long volume, String valueVnd, String marketCapVnd, MetricApplicability applicability,
                String changeBasisReason) {
            this(currency, last, referencePrice, openPrice, highPrice, lowPrice, absoluteChange, percentageChange,
                    direction, volume, valueVnd, marketCapVnd, applicability, changeBasisReason, null, null, null, null);
        }

        public PriceResponse(
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
            this(currency, last, referencePrice, null, null, null,
                    absoluteChange, percentageChange, direction, volume, valueVnd, marketCapVnd,
                    applicability, changeBasisReason, null, null, null, null);
        }
    }

    public record SessionResponse(SessionState state, LocalDate tradingDate, String calendarVersion) {
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}

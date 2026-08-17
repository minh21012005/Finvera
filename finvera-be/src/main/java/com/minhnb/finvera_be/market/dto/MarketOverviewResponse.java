package com.minhnb.finvera_be.market.dto;

import com.minhnb.finvera_be.market.domain.index.IndexOverview;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.IndexCode;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.SessionState;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.Venue;
import com.minhnb.finvera_be.market.service.MarketOverviewService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Version 1.0 transport DTO. Decimal facts are strings to preserve precision in the browser. */
public record MarketOverviewResponse(
        String contractVersion,
        Instant generatedAt,
        LocalDate tradingDate,
        String timezone,
        DataStatus dataStatus,
        SessionResponse session,
        List<IndexResponse> indices,
        BreadthResponse breadth,
        RegimeResponse regime,
        List<WarningResponse> warnings) {

    public static MarketOverviewResponse from(MarketOverviewService.MarketOverview overview) {
        IndexOverview indexOverview = overview.indices();
        List<IndexResponse> indexResponses = indexOverview.indices().stream()
                .map(fact -> IndexResponse.from(fact, indexOverview, sourceFor(fact, indexOverview.source())))
                .toList();
        return new MarketOverviewResponse(
                "1.0", overview.generatedAt(), indexOverview.tradingDate(),
                MarketOverviewService.MARKET_ZONE.getId(), overview.dataStatus(),
                SessionResponse.from(indexOverview), indexResponses,
                BreadthResponse.from(overview.breadth()), RegimeResponse.unavailable(), warnings(indexResponses));
    }

    private static List<WarningResponse> warnings(List<IndexResponse> indices) {
        return indices.stream()
                .filter(index -> index.dataStatus() == DataStatus.UNAVAILABLE)
                .map(index -> new WarningResponse("MISSING_INDEX", "WARNING", "INDEX", index.code().name()))
                .toList();
    }

    private static SourceReference sourceFor(IndexOverview.IndexFact fact, String source) {
        return new SourceReference(fact.dataStatus() == DataStatus.UNAVAILABLE ? "UNAVAILABLE" : source, "INDEX");
    }

    public record SessionResponse(
            SessionState state, LocalDate tradingDate, Instant asOf, String calendarVersion,
            List<VenueSessionResponse> venueStates) {
        static SessionResponse from(IndexOverview overview) {
            return new SessionResponse(overview.sessionState(), overview.tradingDate(), overview.observedAt(),
                    "market-calendar-v1", List.of(
                            new VenueSessionResponse(Venue.HOSE, overview.sessionState()),
                            new VenueSessionResponse(Venue.HNX, overview.sessionState()),
                            new VenueSessionResponse(Venue.UPCOM, overview.sessionState())));
        }
    }

    public record VenueSessionResponse(Venue venue, SessionState state) {
    }

    public record IndexResponse(
            IndexCode code, String displayName, Venue venue, DataStatus dataStatus,
            Object direction, String value, String absoluteChange, String percentageChange,
            Long matchedVolume, String matchedValueVnd, String unit, String currency,
            LocalDate tradingDate, Instant asOf, SourceReference source, Long revision,
            List<String> reasonCodes) {
        static IndexResponse from(IndexOverview.IndexFact fact, IndexOverview overview, SourceReference source) {
            boolean available = fact.dataStatus() != DataStatus.UNAVAILABLE;
            return new IndexResponse(fact.code(), MarketOverviewResponse.displayName(fact.code()), fact.venue(), fact.dataStatus(),
                    fact.direction(), decimal(fact.level()), decimal(fact.absoluteChange()),
                    decimal(fact.percentageChange()), fact.matchedVolume(), decimal(fact.matchedValueVnd()),
                    "INDEX_POINT", "VND", available ? overview.tradingDate() : null,
                    available ? overview.observedAt() : null, source, available ? overview.revision() : null,
                    fact.reasonCodes());
        }
    }

    public record BreadthResponse(
            DataStatus dataStatus, Integer advancing, Integer declining, Integer unchanged,
            Integer eligible, Integer unclassified, String universeVersion, LocalDate tradingDate,
            Instant asOf, SourceReference source, List<String> reasonCodes) {
        static BreadthResponse unavailable() {
            return new BreadthResponse(DataStatus.UNAVAILABLE, null, null, null, null, null,
                    "breadth-universe-v1", null, null,
                    new SourceReference("UNAVAILABLE", "BREADTH"), List.of("BREADTH_NOT_IMPLEMENTED"));
        }
        static BreadthResponse from(com.minhnb.finvera_be.market.service.BreadthService.Snapshot snapshot) {
            if (snapshot == null) return unavailable();
            var result = snapshot.result();
            return new BreadthResponse(snapshot.dataStatus(), result.advancing(), result.declining(), result.unchanged(),
                    result.eligible(), result.unclassified(), snapshot.universeVersion(), snapshot.tradingDate(), snapshot.asOf(),
                    new SourceReference("FINVERA_ACCEPTED", "BREADTH"), result.reasonCodes());
        }
    }

    public record RegimeResponse(
            DataStatus dataStatus, String ruleVersion, Object label, Integer score, Integer confidence,
            String confidenceMeaning, List<Object> factors, LocalDate tradingDate, Instant asOf,
            SourceReference source, List<String> reasonCodes, String disclaimerCode) {
        static RegimeResponse unavailable() {
            return new RegimeResponse(DataStatus.UNAVAILABLE, "market-regime-v1", null, null, null,
                    "ASSESSMENT_QUALITY_NOT_FORECAST_PROBABILITY", List.of(), null, null,
                    new SourceReference("UNAVAILABLE", "REGIME"), List.of("REGIME_NOT_IMPLEMENTED"),
                    "QUANTITATIVE_DECISION_SUPPORT_NOT_INVESTMENT_ADVICE");
        }
    }

    public record SourceReference(String provider, String dataset) {
    }

    public record WarningResponse(String code, String severity, String section, String subject) {
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static String displayName(IndexCode code) {
        return switch (code) {
            case VN_INDEX -> "VN-Index";
            case VN30 -> "VN30-Index";
            case HNX_INDEX -> "HNX-Index";
            case UPCOM_INDEX -> "UPCoM-Index";
        };
    }
}

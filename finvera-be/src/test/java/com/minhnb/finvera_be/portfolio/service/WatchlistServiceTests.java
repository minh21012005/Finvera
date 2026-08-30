package com.minhnb.finvera_be.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.auth.config.OwnerProperties;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.SessionState;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService.InstrumentReference;
import com.minhnb.finvera_be.portfolio.dto.WatchlistItemResponse;
import com.minhnb.finvera_be.portfolio.entity.WatchlistEntity;
import com.minhnb.finvera_be.portfolio.entity.WatchlistItemEntity;
import com.minhnb.finvera_be.portfolio.repository.WatchlistItemRepository;
import com.minhnb.finvera_be.portfolio.repository.WatchlistRepository;
import com.minhnb.finvera_be.stock.service.StockReferenceDataService;
import com.minhnb.finvera_be.stock.service.StockReferenceDataService.DailyBarReference;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contract U-8 (price freshness) and U-9 (daily-change basis) for watchlist
 * items, research R-012 (2026-08-30).
 */
class WatchlistServiceTests {

    private final UUID ownerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID fptId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final UUID watchlistId = UUID.randomUUID();
    // 2026-08-13 is a Thursday.
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-13T08:00:00Z"), ZoneOffset.UTC);

    private WatchlistRepository watchlists;
    private WatchlistItemRepository items;
    private MarketReferenceDataService marketReferenceData;
    private StockReferenceDataService stockReferenceData;
    private WatchlistService service;

    @BeforeEach
    void setUp() {
        watchlists = mock(WatchlistRepository.class);
        items = mock(WatchlistItemRepository.class);
        marketReferenceData = mock(MarketReferenceDataService.class);
        stockReferenceData = mock(StockReferenceDataService.class);
        service = new WatchlistService(watchlists, items, marketReferenceData, stockReferenceData,
                new OwnerScopedAccess(new OwnerProperties(ownerId, "owner", "hash")), clock);

        when(watchlists.findByIdAndOwnerId(watchlistId, ownerId))
                .thenReturn(Optional.of(new WatchlistEntity(watchlistId, ownerId, "WL", Instant.now(clock))));
        when(items.findByIdWatchlistIdOrderByAddedAtAsc(watchlistId))
                .thenReturn(List.of(new WatchlistItemEntity(watchlistId, fptId, Instant.now(clock))));
        when(marketReferenceData.findInstrumentsByIds(any()))
                .thenReturn(List.of(new InstrumentReference(fptId, "HOSE", "FPT", "EQUITY", "ACTIVE")));
        when(marketReferenceData.resolveSession(eq("HOSE"), any())).thenReturn(
                new MarketReferenceDataService.SessionContext(SessionState.OPEN, LocalDate.parse("2026-08-13")));
        when(stockReferenceData.findEquityProfiles(any())).thenReturn(List.of());
        when(stockReferenceData.findCurrentSignalsForInstruments(any())).thenReturn(List.of());
        when(stockReferenceData.findLatestTechnicalIndicators(any())).thenReturn(Map.of());
    }

    @Test
    @DisplayName("U-9: daily change uses the prior accepted close, never the session open")
    void dailyChangeUsesPriorCloseNotOpen() {
        // open 100,000 / close 102,000 today; prior close 101,000.
        // Against the open the change would read +2.00%; the contract basis is +0.99%.
        when(stockReferenceData.findLatestDailyBars(any(), eq(2))).thenReturn(List.of(
                bar(LocalDate.parse("2026-08-12"), "100500", "101000"),
                bar(LocalDate.parse("2026-08-13"), "100000", "102000")));

        WatchlistItemResponse item = service.getWatchlist(watchlistId).items().get(0);

        assertThat(item.currentPrice()).isEqualTo("102000");
        assertThat(new BigDecimal(item.dailyChangePercent())).isEqualByComparingTo("0.990099");
        assertThat(item.dataStatus()).isEqualTo("PARTIAL"); // price CURRENT, trend/volume indicators absent
    }

    @Test
    @DisplayName("U-8: a close from a previous session is DELAYED, not CURRENT")
    void staleWatchlistPriceIsDisclosed() {
        when(stockReferenceData.findLatestDailyBars(any(), eq(2))).thenReturn(List.of(
                bar(LocalDate.parse("2026-08-11"), "99000", "100000"),
                bar(LocalDate.parse("2026-08-12"), "100000", "101000")));

        WatchlistItemResponse item = service.getWatchlist(watchlistId).items().get(0);

        // The price itself is DELAYED; with trend/volume indicators also absent the
        // item-level status is the most actionable of the two (shared DataStatus
        // ordering ranks PARTIAL above DELAYED), while the reason code keeps
        // disclosing the delayed price rather than being overwritten.
        assertThat(item.reasonCode()).isEqualTo("PRICE_DELAYED");
        assertThat(item.dataStatus()).isEqualTo("PARTIAL");
    }

    @Test
    @DisplayName("U-9: with only one accepted bar and no reference price the change is unavailable, not zero")
    void changeIsUnavailableWithoutABasis() {
        when(stockReferenceData.findLatestDailyBars(any(), eq(2))).thenReturn(List.of(
                bar(LocalDate.parse("2026-08-13"), "100000", "102000")));

        WatchlistItemResponse item = service.getWatchlist(watchlistId).items().get(0);

        assertThat(item.currentPrice()).isEqualTo("102000");
        assertThat(item.dailyChangePercent()).isNull();
    }

    private DailyBarReference bar(LocalDate date, String open, String close) {
        return new DailyBarReference(UUID.randomUUID(), fptId, date, new BigDecimal(open), new BigDecimal(close),
                new BigDecimal(open), new BigDecimal(close), 1_000_000L, null, "VNSTOCK_KBS", Instant.now(clock));
    }
}

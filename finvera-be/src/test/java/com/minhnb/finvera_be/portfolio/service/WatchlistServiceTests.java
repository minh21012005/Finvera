package com.minhnb.finvera_be.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.auth.config.OwnerProperties;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService.InstrumentReference;
import com.minhnb.finvera_be.portfolio.dto.AddWatchlistItemRequest;
import com.minhnb.finvera_be.portfolio.dto.CreateWatchlistRequest;
import com.minhnb.finvera_be.portfolio.dto.RenameWatchlistRequest;
import com.minhnb.finvera_be.portfolio.dto.WatchlistDetailResponse;
import com.minhnb.finvera_be.portfolio.dto.WatchlistSummaryResponse;
import com.minhnb.finvera_be.portfolio.entity.WatchlistEntity;
import com.minhnb.finvera_be.portfolio.entity.WatchlistItemEntity;
import com.minhnb.finvera_be.portfolio.entity.WatchlistItemId;
import com.minhnb.finvera_be.portfolio.repository.WatchlistItemRepository;
import com.minhnb.finvera_be.portfolio.repository.WatchlistRepository;
import com.minhnb.finvera_be.portfolio.service.PortfolioExceptions.DuplicateWatchlistNameException;
import com.minhnb.finvera_be.portfolio.service.PortfolioExceptions.UnsupportedInstrumentException;
import com.minhnb.finvera_be.stock.service.StockReferenceDataService;
import com.minhnb.finvera_be.stock.service.StockReferenceDataService.DailyBarReference;
import com.minhnb.finvera_be.stock.service.StockReferenceDataService.EquityProfileReference;
import com.minhnb.finvera_be.stock.service.StockReferenceDataService.SignalReference;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WatchlistServiceTests {

    private final UUID ownerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID fptId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final UUID vnmId = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-15T10:00:00Z"), ZoneOffset.UTC);

    private WatchlistRepository watchlistRepository;
    private WatchlistItemRepository watchlistItemRepository;
    private MarketReferenceDataService marketReferenceData;
    private StockReferenceDataService stockReferenceData;
    private OwnerScopedAccess ownerScopedAccess;
    private WatchlistService watchlistService;

    @BeforeEach
    void setUp() {
        watchlistRepository = mock(WatchlistRepository.class);
        watchlistItemRepository = mock(WatchlistItemRepository.class);
        marketReferenceData = mock(MarketReferenceDataService.class);
        stockReferenceData = mock(StockReferenceDataService.class);
        ownerScopedAccess = new OwnerScopedAccess(new OwnerProperties(ownerId, "owner", "hash"));

        watchlistService = new WatchlistService(
                watchlistRepository,
                watchlistItemRepository,
                marketReferenceData,
                stockReferenceData,
                ownerScopedAccess,
                clock);
    }

    @Test
    @DisplayName("Create watchlist creates entity and returns summary")
    void createWatchlist() {
        when(watchlistRepository.existsByOwnerIdAndName(ownerId, "Tech Stocks")).thenReturn(false);
        when(watchlistRepository.save(any(WatchlistEntity.class))).thenAnswer(i -> i.getArgument(0));

        WatchlistSummaryResponse summary = watchlistService.createWatchlist(new CreateWatchlistRequest("Tech Stocks"));

        assertThat(summary.name()).isEqualTo("Tech Stocks");
        assertThat(summary.itemCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Create duplicate watchlist name throws DuplicateWatchlistNameException")
    void createDuplicateNameThrows() {
        when(watchlistRepository.existsByOwnerIdAndName(ownerId, "Duplicate")).thenReturn(true);

        assertThatThrownBy(() -> watchlistService.createWatchlist(new CreateWatchlistRequest("Duplicate")))
                .isInstanceOf(DuplicateWatchlistNameException.class);
    }

    @Test
    @DisplayName("Rename watchlist updates name")
    void renameWatchlist() {
        UUID wlId = UUID.randomUUID();
        WatchlistEntity entity = new WatchlistEntity(wlId, ownerId, "Old Name", Instant.now(clock));
        when(watchlistRepository.findByIdAndOwnerId(wlId, ownerId)).thenReturn(Optional.of(entity));
        when(watchlistRepository.existsByOwnerIdAndName(ownerId, "New Name")).thenReturn(false);
        when(watchlistItemRepository.countByIdWatchlistId(wlId)).thenReturn(3);

        WatchlistSummaryResponse response = watchlistService.renameWatchlist(wlId, new RenameWatchlistRequest("New Name"));

        assertThat(response.name()).isEqualTo("New Name");
        assertThat(response.itemCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("Delete watchlist deletes items and entity")
    void deleteWatchlist() {
        UUID wlId = UUID.randomUUID();
        WatchlistEntity entity = new WatchlistEntity(wlId, ownerId, "Delete Me", Instant.now(clock));
        when(watchlistRepository.findByIdAndOwnerId(wlId, ownerId)).thenReturn(Optional.of(entity));

        watchlistService.deleteWatchlist(wlId);

        verify(watchlistItemRepository).deleteByIdWatchlistId(wlId);
        verify(watchlistRepository).delete(entity);
    }

    @Test
    @DisplayName("Add item with unsupported symbol throws UnsupportedInstrumentException")
    void addItemUnsupportedSymbol() {
        UUID wlId = UUID.randomUUID();
        when(watchlistRepository.findByIdAndOwnerId(wlId, ownerId))
                .thenReturn(Optional.of(new WatchlistEntity(wlId, ownerId, "WL", Instant.now(clock))));
        when(marketReferenceData.findActiveInstrumentBySymbol("BAD")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> watchlistService.addItem(wlId, new AddWatchlistItemRequest("BAD")))
                .isInstanceOf(UnsupportedInstrumentException.class);
    }

    @Test
    @DisplayName("Get watchlist assembles live price, trend, and signal context truthfully")
    void getWatchlistAssemblesContext() {
        UUID wlId = UUID.randomUUID();
        WatchlistEntity entity = new WatchlistEntity(wlId, ownerId, "My Watchlist", Instant.now(clock));
        when(watchlistRepository.findByIdAndOwnerId(wlId, ownerId)).thenReturn(Optional.of(entity));

        WatchlistItemEntity itemFpt = new WatchlistItemEntity(new WatchlistItemId(wlId, fptId), Instant.parse("2026-08-10T00:00:00Z"));
        WatchlistItemEntity itemVnm = new WatchlistItemEntity(new WatchlistItemId(wlId, vnmId), Instant.parse("2026-08-11T00:00:00Z"));
        when(watchlistItemRepository.findByIdWatchlistIdOrderByAddedAtAsc(wlId)).thenReturn(List.of(itemFpt, itemVnm));

        when(marketReferenceData.findInstrumentsByIds(any())).thenReturn(List.of(
                new InstrumentReference(fptId, "HOSE", "FPT", "EQUITY", "ACTIVE"),
                new InstrumentReference(vnmId, "HOSE", "VNM", "EQUITY", "ACTIVE")));

        when(stockReferenceData.findEquityProfiles(any())).thenReturn(List.of(
                new EquityProfileReference(fptId, "Tập đoàn FPT", "FPT Corp", UUID.randomUUID(), "Công nghệ", "Tech"),
                new EquityProfileReference(vnmId, "Vinamilk", "Vinamilk Corp", UUID.randomUUID(), "Thực phẩm", "Food")));

        DailyBarReference fptBar = new DailyBarReference(
                UUID.randomUUID(), fptId, LocalDate.parse("2026-08-14"), new BigDecimal("58000"),
                new BigDecimal("61000"), new BigDecimal("58000"), new BigDecimal("60000"),
                2000000L, new BigDecimal("120000000000"), "TCBS", Instant.now(clock));
        when(stockReferenceData.findLatestDailyBars(any())).thenReturn(List.of(fptBar));

        // Signal only for FPT, VNM has no signal
        SignalReference fptSig = new SignalReference(
                UUID.randomUUID(), fptId, "MOMENTUM_BREAKOUT", "strategy-signal-v1",
                LocalDate.parse("2026-08-14"), "BULLISH", new BigDecimal("59000"), new BigDecimal("60000"),
                new BigDecimal("57000"), new BigDecimal("65000"), new BigDecimal("70000"),
                new BigDecimal("2.5"), 30, "LOW", Instant.now(clock));
        when(stockReferenceData.findCurrentSignalsForInstruments(any())).thenReturn(List.of(fptSig));

        WatchlistDetailResponse detail = watchlistService.getWatchlist(wlId);

        assertThat(detail.items()).hasSize(2);

        // Item 1: FPT
        var fptItem = detail.items().get(0);
        assertThat(fptItem.symbol()).isEqualTo("FPT");
        assertThat(fptItem.companyName()).isEqualTo("Tập đoàn FPT");
        assertThat(fptItem.currentPrice()).isEqualTo("60000");
        assertThat(fptItem.dataStatus()).isEqualTo("CURRENT");
        assertThat(fptItem.hasCurrentSignal()).isTrue();
        assertThat(fptItem.signalDirection()).isEqualTo("BULLISH");
        assertThat(fptItem.riskLevel()).isEqualTo("LOW");

        // Item 2: VNM (no bar, no signal)
        var vnmItem = detail.items().get(1);
        assertThat(vnmItem.symbol()).isEqualTo("VNM");
        assertThat(vnmItem.companyName()).isEqualTo("Vinamilk");
        assertThat(vnmItem.currentPrice()).isNull();
        assertThat(vnmItem.dataStatus()).isEqualTo("UNAVAILABLE");
        assertThat(vnmItem.reasonCode()).isEqualTo("NO_BARS_AVAILABLE");
        assertThat(vnmItem.hasCurrentSignal()).isFalse();
        assertThat(vnmItem.signalDirection()).isNull();
    }
}

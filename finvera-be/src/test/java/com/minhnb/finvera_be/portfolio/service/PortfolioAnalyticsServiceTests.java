package com.minhnb.finvera_be.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.auth.config.OwnerProperties;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService.InstrumentReference;
import com.minhnb.finvera_be.portfolio.dto.PortfolioAnalyticsResponse;
import com.minhnb.finvera_be.portfolio.entity.PortfolioEntity;
import com.minhnb.finvera_be.portfolio.entity.PortfolioTransactionEntity;
import com.minhnb.finvera_be.portfolio.repository.PortfolioRepository;
import com.minhnb.finvera_be.portfolio.repository.PortfolioTransactionRepository;
import com.minhnb.finvera_be.portfolio.service.PortfolioExceptions.PeriodTooLongException;
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

class PortfolioAnalyticsServiceTests {

    private final UUID ownerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID portfolioId = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private final UUID fptId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-15T10:00:00Z"), ZoneOffset.UTC);

    private PortfolioRepository portfolioRepository;
    private PortfolioTransactionRepository transactionRepository;
    private MarketReferenceDataService marketReferenceData;
    private StockReferenceDataService stockReferenceData;
    private OwnerScopedAccess ownerScopedAccess;
    private PortfolioAnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        portfolioRepository = mock(PortfolioRepository.class);
        transactionRepository = mock(PortfolioTransactionRepository.class);
        marketReferenceData = mock(MarketReferenceDataService.class);
        stockReferenceData = mock(StockReferenceDataService.class);
        ownerScopedAccess = new OwnerScopedAccess(new OwnerProperties(ownerId, "owner", "hash"));

        analyticsService = new PortfolioAnalyticsService(
                portfolioRepository,
                transactionRepository,
                marketReferenceData,
                stockReferenceData,
                ownerScopedAccess,
                clock,
                730L);
    }

    @Test
    @DisplayName("Empty portfolio returns default analytics with no history")
    void emptyPortfolioAnalytics() {
        PortfolioEntity pf = new PortfolioEntity(portfolioId, ownerId, "Empty", Instant.now(clock), null);
        when(portfolioRepository.findByIdAndOwnerIdAndDeletedAtIsNull(portfolioId, ownerId)).thenReturn(Optional.of(pf));
        when(transactionRepository.findByPortfolioIdOrderByExecutedAtAscSequenceNoAsc(portfolioId))
                .thenReturn(Collections.emptyList());

        PortfolioAnalyticsResponse res = analyticsService.getPortfolioAnalytics(portfolioId, null, null);

        assertThat(res.returnSinceInception()).isNull();
        assertThat(res.returnOverPeriod()).isNull();
        assertThat(res.performanceHistory()).isEmpty();
        assertThat(res.stockConcentration()).isEmpty();
        assertThat(res.sectorConcentration()).isEmpty();
        assertThat(res.riskExposure().coverageRatio()).isEqualTo("0");
    }

    @Test
    @DisplayName("Portfolio with transactions clamps periodFrom to inception when requestedFrom predates inception (F6)")
    void clampsPeriodFromToInception() {
        PortfolioEntity pf = new PortfolioEntity(portfolioId, ownerId, "Main", Instant.now(clock), null);
        when(portfolioRepository.findByIdAndOwnerIdAndDeletedAtIsNull(portfolioId, ownerId)).thenReturn(Optional.of(pf));

        Instant tx1Time = Instant.parse("2026-08-01T02:00:00Z");
        PortfolioTransactionEntity txDeposit = new PortfolioTransactionEntity(
                UUID.randomUUID(), portfolioId, "key-1", "DEPOSIT",
                null, null, null, BigDecimal.ZERO,
                new BigDecimal("100000000"), "VND", tx1Time, tx1Time, null, null);

        when(transactionRepository.findByPortfolioIdOrderByExecutedAtAscSequenceNoAsc(portfolioId))
                .thenReturn(List.of(txDeposit));

        LocalDate requestedFrom = LocalDate.parse("2026-01-01");
        PortfolioAnalyticsResponse res = analyticsService.getPortfolioAnalytics(portfolioId, requestedFrom, null);

        assertThat(res.periodClampedToInception()).isTrue();
        assertThat(res.periodFrom()).isEqualTo(LocalDate.parse("2026-08-01"));
    }

    @Test
    @DisplayName("Throws PeriodTooLongException when requested span exceeds max days")
    void periodTooLongThrows() {
        PortfolioEntity pf = new PortfolioEntity(portfolioId, ownerId, "Main", Instant.now(clock), null);
        when(portfolioRepository.findByIdAndOwnerIdAndDeletedAtIsNull(portfolioId, ownerId)).thenReturn(Optional.of(pf));

        Instant tx1Time = Instant.parse("2020-01-01T02:00:00Z");
        PortfolioTransactionEntity txDeposit = new PortfolioTransactionEntity(
                UUID.randomUUID(), portfolioId, "key-1", "DEPOSIT",
                null, null, null, BigDecimal.ZERO,
                new BigDecimal("100000000"), "VND", tx1Time, tx1Time, null, null);

        when(transactionRepository.findByPortfolioIdOrderByExecutedAtAscSequenceNoAsc(portfolioId))
                .thenReturn(List.of(txDeposit));

        LocalDate requestedFrom = LocalDate.parse("2020-01-01");
        LocalDate requestedTo = LocalDate.parse("2025-01-01"); // > 730 days

        assertThatThrownBy(() -> analyticsService.getPortfolioAnalytics(portfolioId, requestedFrom, requestedTo))
                .isInstanceOf(PeriodTooLongException.class);
    }

    @Test
    @DisplayName("Calculates return, stock and sector concentration, and risk exposure with partial coverage")
    void comprehensiveAnalyticsCalculation() {
        PortfolioEntity pf = new PortfolioEntity(portfolioId, ownerId, "Main", Instant.now(clock), null);
        when(portfolioRepository.findByIdAndOwnerIdAndDeletedAtIsNull(portfolioId, ownerId)).thenReturn(Optional.of(pf));

        Instant t0 = Instant.parse("2026-08-01T02:00:00Z");
        Instant t1 = Instant.parse("2026-08-02T02:00:00Z");

        PortfolioTransactionEntity txDeposit = new PortfolioTransactionEntity(
                UUID.randomUUID(), portfolioId, "key-1", "DEPOSIT",
                null, null, null, BigDecimal.ZERO,
                new BigDecimal("100000000"), "VND", t0, t0, null, null);

        PortfolioTransactionEntity txBuyFpt = new PortfolioTransactionEntity(
                UUID.randomUUID(), portfolioId, "key-2", "BUY",
                fptId, new BigDecimal("1000"), new BigDecimal("50000"),
                BigDecimal.ZERO, null, "VND", t1, t1, null, null);

        when(transactionRepository.findByPortfolioIdOrderByExecutedAtAscSequenceNoAsc(portfolioId))
                .thenReturn(List.of(txDeposit, txBuyFpt));

        when(marketReferenceData.findInstrumentsByIds(any())).thenReturn(List.of(
                new InstrumentReference(fptId, "HOSE", "FPT", "EQUITY", "ACTIVE")));

        when(stockReferenceData.findLatestDailyBars(any())).thenReturn(List.of(
                new DailyBarReference(UUID.randomUUID(), fptId, LocalDate.parse("2026-08-14"),
                        new BigDecimal("55000"), new BigDecimal("60000"), new BigDecimal("55000"),
                        new BigDecimal("60000"), 1000000L, new BigDecimal("60000000000"), "TCBS", Instant.now(clock))));

        when(stockReferenceData.findEquityProfiles(any())).thenReturn(List.of(
                new EquityProfileReference(fptId, "Tập đoàn FPT", "FPT Corp", UUID.randomUUID(), "Công nghệ Thông tin", "Technology")));

        when(stockReferenceData.findCurrentSignalsForInstruments(any())).thenReturn(List.of(
                new SignalReference(UUID.randomUUID(), fptId, "MOMENTUM", "v1", LocalDate.parse("2026-08-14"),
                        "BULLISH", new BigDecimal("58000"), new BigDecimal("60000"), new BigDecimal("55000"),
                        new BigDecimal("65000"), new BigDecimal("70000"), new BigDecimal("2.0"),
                        25, "LOW", Instant.now(clock))));

        PortfolioAnalyticsResponse res = analyticsService.getPortfolioAnalytics(portfolioId, LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-15"));

        // Net contributed: 100M. Total value: 50M cash + 1000*60k = 110M. Return = (110 - 100) / 100 = 0.1 (10%)
        assertThat(res.returnSinceInception()).isEqualTo("0.1");
        assertThat(res.stockConcentration()).hasSize(1);
        assertThat(res.stockConcentration().get(0).key()).isEqualTo("FPT");
        assertThat(res.sectorConcentration()).hasSize(1);
        assertThat(res.sectorConcentration().get(0).key()).isEqualTo("Công nghệ Thông tin");

        // Risk exposure
        assertThat(res.riskExposure().riskExposureScore()).isEqualTo(25);
        assertThat(res.riskExposure().riskExposureLevel()).isEqualTo("LOW");
        assertThat(res.riskExposure().coverageRatio()).isEqualTo("1");

        // Benchmark
        assertThat(res.benchmark().benchmarkSymbol()).isEqualTo("VNINDEX");
    }
}

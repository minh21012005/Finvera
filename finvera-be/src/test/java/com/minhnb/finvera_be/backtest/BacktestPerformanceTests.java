package com.minhnb.finvera_be.backtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.backtest.domain.BacktestTypes.Assumptions;
import com.minhnb.finvera_be.backtest.domain.BacktestTypes.Costs;
import com.minhnb.finvera_be.backtest.domain.BacktestTypes.RunStatus;
import com.minhnb.finvera_be.backtest.domain.BacktestTypes.SessionBar;
import com.minhnb.finvera_be.backtest.dto.BacktestDtos.CostPolicy;
import com.minhnb.finvera_be.backtest.dto.BacktestDtos.CreateRequest;
import com.minhnb.finvera_be.backtest.entity.BacktestRunEntity;
import com.minhnb.finvera_be.backtest.repository.BacktestEntryEventRepository;
import com.minhnb.finvera_be.backtest.repository.BacktestEquityPointRepository;
import com.minhnb.finvera_be.backtest.repository.BacktestEvidenceRepository;
import com.minhnb.finvera_be.backtest.repository.BacktestMetricRepository;
import com.minhnb.finvera_be.backtest.repository.BacktestRunRepository;
import com.minhnb.finvera_be.backtest.repository.BacktestTradeRepository;
import com.minhnb.finvera_be.backtest.service.BacktestCommandService;
import com.minhnb.finvera_be.backtest.service.BacktestExecutionService;
import com.minhnb.finvera_be.market.service.BacktestMarketRuleService;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.portfolio.service.OwnerScopedAccess;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.StrategyCode;
import com.minhnb.finvera_be.stock.domain.strategy.StrategySignalV1;
import com.minhnb.finvera_be.stock.service.BacktestHistoryDataService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.context.ApplicationEventPublisher;

class BacktestPerformanceTests {
    private static final Instant NOW = Instant.parse("2026-09-14T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    @Timeout(60)
    void createCommandP95RemainsBelowOneSecond() {
        var runs = mock(BacktestRunRepository.class);
        var market = mock(MarketReferenceDataService.class);
        var marketRules = mock(BacktestMarketRuleService.class);
        var owners = mock(OwnerScopedAccess.class);
        var events = mock(ApplicationEventPublisher.class);
        when(owners.getAuthenticatedOwnerId()).thenReturn(UUID.randomUUID());
        when(runs.findByOwnerIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(market.findInstrumentBySymbolIncludingDelisted("FPT")).thenReturn(Optional.of(
                new MarketReferenceDataService.InstrumentReference(UUID.randomUUID(), "HOSE", "FPT", "EQUITY",
                        "LISTED")));
        when(marketRules.isKnownTradingSession(any(), any())).thenReturn(true);
        var service = new BacktestCommandService(runs, market, owners, events, CLOCK, marketRules);
        var request = new CreateRequest(StrategyCode.RSI_BASED, "FPT", LocalDate.of(2025, 1, 2),
                LocalDate.of(2025, 12, 31), "100000000", "0.01", "0.04",
                new CostPolicy(true, null, null, null, null, null));
        var durations = new ArrayList<Long>();

        for (int i = 0; i < 100; i++) {
            long started = System.nanoTime();
            service.create(request, "perf-" + i);
            durations.add(System.nanoTime() - started);
        }

        assertThat(p95Millis(durations)).isLessThan(1_000L);
    }

    @Test
    @Timeout(60)
    void tenYearExecutionPathP95ReachesTerminalStateBelowSixtySeconds() {
        var runs = mock(BacktestRunRepository.class);
        var history = mock(BacktestHistoryDataService.class);
        var market = mock(BacktestMarketRuleService.class);
        var sessions = tenYearsOfSessions();
        var snapshot = new BacktestHistoryDataService.Snapshot(
                sessions, "a".repeat(64), "RAW", List.of("PERFORMANCE_FIXTURE"), null);
        when(history.load(any(), any(), any(), any(), any())).thenReturn(snapshot);
        when(market.isKnownTradingSession(anyString(), any())).thenReturn(true);
        when(market.rules(anyString(), any())).thenReturn(
                new BacktestMarketRuleService.Rules(100, "market-lot-v1", "PERFORMANCE_FIXTURE", NOW));
        var service = new BacktestExecutionService(runs, mock(BacktestTradeRepository.class),
                mock(BacktestEquityPointRepository.class), mock(BacktestMetricRepository.class),
                mock(BacktestEntryEventRepository.class), mock(BacktestEvidenceRepository.class), history, market,
                CLOCK);
        var durations = new ArrayList<Long>();

        for (int i = 0; i < 20; i++) {
            var run = runningRun(sessions.get(0).bar().tradingDate(),
                    sessions.get(sessions.size() - 1).bar().tradingDate());
            when(runs.findById(run.getId())).thenReturn(Optional.of(run));
            long started = System.nanoTime();
            service.execute(run.getId());
            durations.add(System.nanoTime() - started);
            assertThat(run.getStatus()).isEqualTo(RunStatus.COMPLETED);
            assertThat(run.getProcessedSessions()).isEqualTo(2_520);
        }

        assertThat(p95Millis(durations)).isLessThan(60_000L);
    }

    private static List<BacktestHistoryDataService.HistoricalSession> tenYearsOfSessions() {
        List<BacktestHistoryDataService.HistoricalSession> sessions = new ArrayList<>();
        LocalDate date = LocalDate.of(2016, 1, 4);
        for (int i = 0; i < 2_520; i++) {
            while (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                date = date.plusDays(1);
            }
            var acceptedAt = date.atStartOfDay(ZoneOffset.UTC).toInstant();
            var bar = new SessionBar(UUID.randomUUID(), date, decimal("100"), decimal("101"), decimal("99"),
                    decimal("100"), BigDecimal.ONE, "RAW", "PERFORMANCE_FIXTURE", acceptedAt, acceptedAt);
            var evaluation = new StrategySignalV1.EntryEvaluation(StrategyCode.RSI_BASED,
                    StrategySignalV1.EntryStatus.NO_SIGNAL, null, null, null, Map.of());
            sessions.add(new BacktestHistoryDataService.HistoricalSession(bar, true, evaluation));
            date = date.plusDays(1);
        }
        return List.copyOf(sessions);
    }

    private static BacktestRunEntity runningRun(LocalDate start, LocalDate end) {
        var zero = BigDecimal.ZERO;
        var run = new BacktestRunEntity(UUID.randomUUID(), UUID.randomUUID(), null, "RSI_BASED", "FPT",
                UUID.randomUUID(), "HOSE", start, end,
                new Assumptions(decimal("100000000"), decimal("0.01"), decimal("0.04"),
                        new Costs(zero, zero, zero, zero, zero, true)), NOW, NOW);
        run.claim(NOW);
        return run;
    }

    private static long p95Millis(List<Long> nanoseconds) {
        var sorted = new ArrayList<>(nanoseconds);
        Collections.sort(sorted);
        int index = Math.max(0, (int) Math.ceil(sorted.size() * 0.95) - 1);
        return sorted.get(index) / 1_000_000;
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}

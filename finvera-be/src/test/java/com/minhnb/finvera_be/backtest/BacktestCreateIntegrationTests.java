package com.minhnb.finvera_be.backtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.backtest.domain.BacktestTypes.Assumptions;
import com.minhnb.finvera_be.backtest.domain.BacktestTypes.Costs;
import com.minhnb.finvera_be.backtest.domain.BacktestTypes.RunStatus;
import com.minhnb.finvera_be.backtest.domain.BacktestTypes.SessionBar;
import com.minhnb.finvera_be.backtest.entity.BacktestRunEntity;
import com.minhnb.finvera_be.backtest.repository.BacktestEntryEventRepository;
import com.minhnb.finvera_be.backtest.repository.BacktestEquityPointRepository;
import com.minhnb.finvera_be.backtest.repository.BacktestEvidenceRepository;
import com.minhnb.finvera_be.backtest.repository.BacktestMetricRepository;
import com.minhnb.finvera_be.backtest.repository.BacktestRunRepository;
import com.minhnb.finvera_be.backtest.repository.BacktestTradeRepository;
import com.minhnb.finvera_be.backtest.service.BacktestExecutionService;
import com.minhnb.finvera_be.market.service.BacktestMarketRuleService;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.StrategyCode;
import com.minhnb.finvera_be.stock.domain.strategy.StrategySignalV1;
import com.minhnb.finvera_be.stock.service.BacktestHistoryDataService;
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
import org.junit.jupiter.api.Test;

class BacktestCreateIntegrationTests {
    private static final Instant NOW = Instant.parse("2026-09-14T08:00:00Z");
    private final BacktestRunRepository runs = mock(BacktestRunRepository.class);
    private final BacktestTradeRepository trades = mock(BacktestTradeRepository.class);
    private final BacktestEquityPointRepository equity = mock(BacktestEquityPointRepository.class);
    private final BacktestMetricRepository metrics = mock(BacktestMetricRepository.class);
    private final BacktestEntryEventRepository events = mock(BacktestEntryEventRepository.class);
    private final BacktestEvidenceRepository evidence = mock(BacktestEvidenceRepository.class);
    private final BacktestHistoryDataService history = mock(BacktestHistoryDataService.class);
    private final BacktestMarketRuleService market = mock(BacktestMarketRuleService.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private BacktestExecutionService service;

    @BeforeEach
    void setUp() {
        service = new BacktestExecutionService(runs, trades, equity, metrics, events, evidence, history, market, clock);
        when(market.rules(any(), any())).thenReturn(new BacktestMarketRuleService.Rules(
                100, "market-lot-v1", "VNX_DECISION_22_QD_HDTV_2026", NOW));
    }

    @Test
    void completesAConsistentSnapshotAndPersistsImmutableEvidence() {
        var run = runningRun();
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));
        when(market.isKnownTradingSession("HOSE", LocalDate.of(2026, 9, 11))).thenReturn(true);
        when(history.load(any(), any(), any(), any(), any())).thenReturn(snapshot(noSignalSession()));

        service.execute(run.getId());

        assertThat(run.getStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(run.getInputFingerprint()).isEqualTo("a".repeat(64));
        assertThat(run.getProcessedSessions()).isOne();
        verify(evidence).saveAll(any());
        verify(equity).saveAll(any());
        verify(metrics).saveAll(any());
    }

    @Test
    void withholdsAnIncoherentTradingCalendarWithoutPublishingChildren() {
        var run = runningRun();
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));
        when(market.isKnownTradingSession(any(), any())).thenReturn(false);
        when(history.load(any(), any(), any(), any(), any())).thenReturn(snapshot(noSignalSession()));

        service.execute(run.getId());

        assertThat(run.getStatus()).isEqualTo(RunStatus.WITHHELD);
        assertThat(run.getReasonCode()).isEqualTo("INCOHERENT_TRADING_CALENDAR");
        verify(trades, never()).saveAll(any());
        verify(evidence, never()).saveAll(any());
    }

    @Test
    void withholdsUnsupportedCorporateActionBeforeSimulation() {
        var run = runningRun();
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));
        when(history.load(any(), any(), any(), any(), any())).thenReturn(
                new BacktestHistoryDataService.Snapshot(List.of(), null, null, List.of(),
                        "UNSUPPORTED_CORPORATE_ACTION"));

        service.execute(run.getId());

        assertThat(run.getStatus()).isEqualTo(RunStatus.WITHHELD);
        assertThat(run.getReasonCode()).isEqualTo("UNSUPPORTED_CORPORATE_ACTION");
        verify(market, never()).rules(any(), any());
        verify(trades, never()).saveAll(any());
    }

    private static BacktestHistoryDataService.Snapshot snapshot(
            BacktestHistoryDataService.HistoricalSession session) {
        return new BacktestHistoryDataService.Snapshot(
                List.of(session), "a".repeat(64), "RAW", List.of("VNSTOCK_VCI"), null);
    }

    private static BacktestHistoryDataService.HistoricalSession noSignalSession() {
        var date = LocalDate.of(2026, 9, 11);
        var bar = new SessionBar(UUID.randomUUID(), date, bd("100"), bd("102"), bd("99"), bd("101"),
                BigDecimal.ONE, "RAW", "VNSTOCK_VCI", NOW, NOW.minusSeconds(60));
        var evaluation = new StrategySignalV1.EntryEvaluation(StrategyCode.RSI_BASED,
                StrategySignalV1.EntryStatus.NO_SIGNAL, null, null, null, Map.of());
        return new BacktestHistoryDataService.HistoricalSession(bar, true, evaluation);
    }

    private static BacktestRunEntity runningRun() {
        var zero = BigDecimal.ZERO;
        var run = new BacktestRunEntity(UUID.randomUUID(), UUID.randomUUID(), null, "RSI_BASED", "FPT",
                UUID.randomUUID(), "HOSE", LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 11),
                new Assumptions(bd("100000000"), bd("0.01"), bd("0.04"),
                        new Costs(zero, zero, zero, zero, zero, true)), NOW, NOW);
        run.claim(NOW);
        return run;
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}

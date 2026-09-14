package com.minhnb.finvera_be.backtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.backtest.domain.BacktestTypes.Assumptions;
import com.minhnb.finvera_be.backtest.domain.BacktestTypes.Availability;
import com.minhnb.finvera_be.backtest.domain.BacktestTypes.Costs;
import com.minhnb.finvera_be.backtest.domain.BacktestTypes.EvidenceUnit;
import com.minhnb.finvera_be.backtest.domain.BacktestTypes.MetricCode;
import com.minhnb.finvera_be.backtest.domain.BacktestTypes.MetricUnit;
import com.minhnb.finvera_be.backtest.entity.BacktestResultEntities.Evidence;
import com.minhnb.finvera_be.backtest.entity.BacktestResultEntities.Metric;
import com.minhnb.finvera_be.backtest.entity.BacktestRunEntity;
import com.minhnb.finvera_be.backtest.repository.BacktestEntryEventRepository;
import com.minhnb.finvera_be.backtest.repository.BacktestEquityPointRepository;
import com.minhnb.finvera_be.backtest.repository.BacktestEvidenceRepository;
import com.minhnb.finvera_be.backtest.repository.BacktestMetricRepository;
import com.minhnb.finvera_be.backtest.repository.BacktestRunRepository;
import com.minhnb.finvera_be.backtest.repository.BacktestTradeRepository;
import com.minhnb.finvera_be.backtest.service.BacktestQueryService;
import com.minhnb.finvera_be.portfolio.service.OwnerScopedAccess;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class BacktestResultContractTests {
    private static final UUID OWNER = UUID.randomUUID();
    private final BacktestRunRepository runs = mock(BacktestRunRepository.class);
    private final BacktestTradeRepository trades = mock(BacktestTradeRepository.class);
    private final BacktestEquityPointRepository equity = mock(BacktestEquityPointRepository.class);
    private final BacktestMetricRepository metrics = mock(BacktestMetricRepository.class);
    private final BacktestEntryEventRepository events = mock(BacktestEntryEventRepository.class);
    private final BacktestEvidenceRepository evidence = mock(BacktestEvidenceRepository.class);
    private final OwnerScopedAccess owners = mock(OwnerScopedAccess.class);
    private BacktestQueryService service;

    @BeforeEach
    void setUp() {
        when(owners.getAuthenticatedOwnerId()).thenReturn(OWNER);
        service = new BacktestQueryService(runs, trades, equity, metrics, events, evidence, owners);
    }

    @Test
    void returnsPersistedEvidenceAndStableWarningsWithoutRecalculation() {
        var run = completedRun();
        when(runs.findByIdAndOwnerId(run.getId(), OWNER)).thenReturn(Optional.of(run));
        when(metrics.findAllByRunIdOrderByCode(run.getId())).thenReturn(List.of(
                new Metric(UUID.randomUUID(), run.getId(), MetricCode.TOTAL_RETURN, bd("0.125"),
                        MetricUnit.RATE, Availability.DEFINED, null)));
        when(evidence.findAllByRunIdOrderByKey(run.getId())).thenReturn(List.of(
                new Evidence(UUID.randomUUID(), run.getId(), "inputFingerprint", "b".repeat(64), EvidenceUnit.TEXT),
                new Evidence(UUID.randomUUID(), run.getId(), "priceAdjustmentBasis", "PROVIDER_ADJUSTED", EvidenceUnit.TEXT)));

        var detail = service.detail(run.getId());

        assertThat(detail.metrics()).singleElement().satisfies(metric -> {
            assertThat(metric.code()).isEqualTo(MetricCode.TOTAL_RETURN);
            assertThat(metric.value()).isEqualTo("0.125");
        });
        assertThat(detail.evidence()).extracting(com.minhnb.finvera_be.backtest.dto.BacktestDtos.Evidence::key)
                .containsExactly("inputFingerprint", "priceAdjustmentBasis");
        assertThat(detail.warnings()).contains("COSTS_EXCLUDED", "CURRENT_MARKET_LOT_APPLIED_HISTORICALLY",
                "SURVIVORSHIP_BIAS_NOT_ELIMINATED", "SUSPENSION_DELISTING_COVERAGE_LIMITED",
                "PROVIDER_ADJUSTED_EXECUTION_BASIS");
        assertThatThrownBy(() -> detail.warnings().add("MUTATED"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void mapsOffsetAndLimitToTheOrderedTradePageContract() {
        var run = completedRun();
        when(runs.findByIdAndOwnerId(run.getId(), OWNER)).thenReturn(Optional.of(run));
        when(trades.findAllByRunIdOrderBySequenceNo(org.mockito.ArgumentMatchers.eq(run.getId()),
                org.mockito.ArgumentMatchers.any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        var page = service.trades(run.getId(), 25, 50);

        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(trades).findAllByRunIdOrderBySequenceNo(org.mockito.ArgumentMatchers.eq(run.getId()), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(25);
        assertThat(page.items()).isEmpty();
    }

    @Test
    void rejectsChildResultsUntilTheRunIsTerminal() {
        var run = queuedRun();
        when(runs.findByIdAndOwnerId(run.getId(), OWNER)).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> service.trades(run.getId(), 25, 0))
                .hasMessage("BACKTEST_RESULT_NOT_AVAILABLE");
    }

    private static BacktestRunEntity completedRun() {
        var run = queuedRun();
        var now = Instant.parse("2026-09-14T08:00:00Z");
        run.claim(now);
        run.complete("b".repeat(64), 20, now.plusSeconds(1));
        return run;
    }

    private static BacktestRunEntity queuedRun() {
        var now = Instant.parse("2026-09-14T08:00:00Z");
        var zero = BigDecimal.ZERO;
        return new BacktestRunEntity(UUID.randomUUID(), OWNER, null, "RSI_BASED", "FPT", UUID.randomUUID(),
                "HOSE", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 11),
                new Assumptions(bd("100000000"), bd("0.01"), bd("0.04"),
                        new Costs(zero, zero, zero, zero, zero, true)), now, now);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}

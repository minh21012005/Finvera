package com.minhnb.finvera_be.backtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.backtest.config.BacktestWorkerConfig.Properties;
import com.minhnb.finvera_be.backtest.domain.BacktestTypes.Assumptions;
import com.minhnb.finvera_be.backtest.domain.BacktestTypes.Costs;
import com.minhnb.finvera_be.backtest.domain.BacktestTypes.RunStatus;
import com.minhnb.finvera_be.backtest.entity.BacktestRunEntity;
import com.minhnb.finvera_be.backtest.repository.BacktestRunRepository;
import com.minhnb.finvera_be.backtest.service.BacktestCommandService;
import com.minhnb.finvera_be.backtest.service.BacktestExecutionService;
import com.minhnb.finvera_be.backtest.service.BacktestMetrics;
import com.minhnb.finvera_be.backtest.service.BacktestWorker;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class BacktestWorkerRecoveryTests {
    private static final Instant NOW = Instant.parse("2026-09-14T08:00:00Z");

    @Test
    void permitsOneRequeueThenExhaustsSecondAttempt() {
        var run = run();
        run.claim(NOW);
        run.requeue();
        run.claim(NOW);
        run.requeue();

        assertThatThrownBy(() -> run.claim(NOW)).hasMessage("ATTEMPTS_EXHAUSTED");
    }

    @Test
    void terminalRunCannotBeRetriedOrMutated() {
        var run = run();
        run.claim(NOW);
        run.fail("BROKEN", NOW);

        assertThatThrownBy(() -> run.claim(NOW)).hasMessage("INVALID_RUN_TRANSITION");
        assertThatThrownBy(() -> run.progress(1, 1, NOW)).hasMessage("INVALID_RUN_TRANSITION");
    }

    @Test
    void activeExecutionPublishesHeartbeatAndCompletesOnce() {
        var runs = mock(BacktestRunRepository.class);
        var execution = mock(BacktestExecutionService.class);
        var metrics = mock(BacktestMetrics.class);
        var executor = directExecutor();
        var run = run();
        var workerRef = new BacktestWorker[1];
        when(runs.claim(run.getId(), NOW)).thenAnswer(ignored -> {
            run.claim(NOW);
            return 1;
        });
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));
        doAnswer(ignored -> {
            workerRef[0].heartbeat();
            run.complete("c".repeat(64), 1, NOW.plusSeconds(1));
            return null;
        }).when(execution).execute(run.getId());
        var worker = worker(runs, execution, executor, metrics);
        workerRef[0] = worker;

        worker.queued(new BacktestCommandService.RunQueued(run.getId()));

        verify(runs).heartbeat(run.getId(), NOW);
        verify(execution).execute(run.getId());
        assertThat(run.getStatus()).isEqualTo(RunStatus.COMPLETED);
    }

    @Test
    void staleFirstAttemptReturnsToQueue() {
        var runs = mock(BacktestRunRepository.class);
        var run = run();
        run.claim(NOW.minus(Duration.ofMinutes(10)));
        when(runs.findStaleIds(any(), any())).thenReturn(List.of(run.getId()));
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));

        worker(runs, mock(BacktestExecutionService.class), mock(ExecutorService.class), mock(BacktestMetrics.class))
                .recover();

        assertThat(run.getStatus()).isEqualTo(RunStatus.QUEUED);
        assertThat(run.getAttemptCount()).isOne();
    }

    @Test
    void staleSecondAttemptFailsPermanently() {
        var runs = mock(BacktestRunRepository.class);
        var run = run();
        run.claim(NOW.minus(Duration.ofMinutes(20)));
        run.requeue();
        run.claim(NOW.minus(Duration.ofMinutes(10)));
        when(runs.findStaleIds(any(), any())).thenReturn(List.of(run.getId()));
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));

        worker(runs, mock(BacktestExecutionService.class), mock(ExecutorService.class), mock(BacktestMetrics.class))
                .recover();

        assertThat(run.getStatus()).isEqualTo(RunStatus.FAILED);
        assertThat(run.getReasonCode()).isEqualTo("WORKER_ATTEMPTS_EXHAUSTED");
    }

    private static BacktestWorker worker(BacktestRunRepository runs, BacktestExecutionService execution,
            ExecutorService executor, BacktestMetrics metrics) {
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        return new BacktestWorker(runs, execution, executor,
                new Properties(true, 1, Duration.ofMinutes(5)), Clock.fixed(NOW, ZoneOffset.UTC), manager, metrics);
    }

    private static ExecutorService directExecutor() {
        var executor = mock(ExecutorService.class);
        when(executor.submit(any(Runnable.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return CompletableFuture.completedFuture(null);
        });
        return executor;
    }

    private static BacktestRunEntity run() {
        var zero = BigDecimal.ZERO;
        return new BacktestRunEntity(UUID.randomUUID(), UUID.randomUUID(), null, "RSI_BASED", "FPT",
                UUID.randomUUID(), "HOSE", LocalDate.of(2025, 1, 2), LocalDate.of(2025, 2, 3),
                new Assumptions(new BigDecimal("1000000"), new BigDecimal("0.01"), new BigDecimal("0.04"),
                        new Costs(zero, zero, zero, zero, zero, true)), NOW, NOW);
    }
}

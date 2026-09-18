package com.minhnb.finvera_be.backtest.service;

import com.minhnb.finvera_be.backtest.config.BacktestWorkerConfig.Properties;
import com.minhnb.finvera_be.backtest.domain.BacktestTypes.RunStatus;
import com.minhnb.finvera_be.backtest.entity.BacktestRunEntity;
import com.minhnb.finvera_be.backtest.repository.BacktestRunRepository;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class BacktestWorker {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BacktestWorker.class);
    private final BacktestRunRepository runs;
    private final BacktestExecutionService execution;
    private final ExecutorService executor;
    private final Properties properties;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final BacktestMetrics metrics;
    private final Set<UUID> activeRuns = ConcurrentHashMap.newKeySet();

    public BacktestWorker(BacktestRunRepository runs, BacktestExecutionService execution,
            ExecutorService backtestExecutor, Properties properties, Clock clock,
            PlatformTransactionManager manager, BacktestMetrics metrics) {
        this.runs = runs;
        this.execution = execution;
        this.executor = backtestExecutor;
        this.properties = properties;
        this.clock = clock;
        this.tx = new TransactionTemplate(manager);
        this.metrics = metrics;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void queued(BacktestCommandService.RunQueued event) {
        if (properties.enabled()) submit(event.runId());
    }

    @Scheduled(fixedDelayString = "${finvera.backtest.worker.poll-delay:5s}")
    public void poll() {
        if (!properties.enabled()) return;
        runs.findQueuedIds(PageRequest.of(0, properties.concurrency())).forEach(this::submit);
    }

    @Scheduled(fixedDelayString = "${finvera.backtest.worker.heartbeat-delay:30s}")
    public void heartbeat() {
        if (!properties.enabled()) return;
        for (UUID id : activeRuns) {
            tx.executeWithoutResult(ignored -> runs.heartbeat(id, clock.instant()));
        }
    }

    @Scheduled(fixedDelayString = "${finvera.backtest.worker.recovery-delay:60s}")
    public void recover() {
        if (!properties.enabled()) return;
        var cutoff = clock.instant().minus(properties.staleAfter());
        for (UUID id : runs.findStaleIds(cutoff, PageRequest.of(0, 100))) {
            tx.executeWithoutResult(ignored -> {
                var run = runs.findById(id).orElse(null);
                if (run == null || run.getStatus() != RunStatus.RUNNING) return;
                if (run.getAttemptCount() < 2) {
                    run.requeue();
                    metrics.recovery("REQUEUED");
                } else {
                    run.fail("WORKER_ATTEMPTS_EXHAUSTED", clock.instant());
                    metrics.recovery("FAILED");
                    metrics.terminal(run);
                }
            });
        }
    }

    private void submit(UUID id) {
        try {
            executor.submit(() -> executeClaimed(id));
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // Run vẫn QUEUED và lượt poll sau sẽ lấy lại.
        }
    }

    private void executeClaimed(UUID id) {
        boolean claimed = Boolean.TRUE.equals(tx.execute(ignored -> runs.claim(id, clock.instant()) == 1));
        if (!claimed) return;
        activeRuns.add(id);
        var timer = metrics.start();
        try {
            execution.execute(id);
            runs.findById(id).ifPresent(metrics::terminal);
        } catch (Exception exception) {
            log.error("Backtest execution failed for run: {}", id, exception);
            tx.executeWithoutResult(ignored -> {
                var run = runs.findById(id).orElse(null);
                if (run != null && run.getStatus() == RunStatus.RUNNING) {
                    if (run.getAttemptCount() < 2) {
                        run.requeue();
                        metrics.recovery("REQUEUED");
                    } else {
                        run.fail("BACKTEST_EXECUTION_FAILED", clock.instant());
                        metrics.recovery("FAILED");
                        metrics.terminal(run);
                    }
                }
            });
        } finally {
            activeRuns.remove(id);
            metrics.stop(timer);
        }
    }
}

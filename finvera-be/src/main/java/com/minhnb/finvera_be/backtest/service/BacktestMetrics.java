package com.minhnb.finvera_be.backtest.service;

import com.minhnb.finvera_be.backtest.domain.BacktestTypes.RunStatus;
import com.minhnb.finvera_be.backtest.entity.BacktestRunEntity;
import com.minhnb.finvera_be.backtest.repository.BacktestRunRepository;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class BacktestMetrics {
    private static final Set<String> OBSERVABLE_REASONS = Set.of(
            "NONE", "OTHER", "BACKTEST_EXECUTION_FAILED", "WORKER_ATTEMPTS_EXHAUSTED",
            "CORPORATE_ACTION_UNSUPPORTED", "UNSUPPORTED_CORPORATE_ACTION",
            "INCOHERENT_TRADING_CALENDAR", "INCOHERENT_PRICE_HISTORY",
            "INCOHERENT_INDICATOR_WINDOW", "PRICE_HISTORY_UNAVAILABLE",
            "ADJUSTMENT_BASIS_UNAVAILABLE", "DUAL_PRICE_BASIS_UNSUPPORTED",
            "INDICATOR_PRICE_BASIS_MISMATCH", "EXECUTION_PRICE_UNAVAILABLE");
    private final MeterRegistry meters;
    private final Timer queueTime;
    private final DistributionSummary processedSessions;
    private final DistributionSummary attemptCount;

    public BacktestMetrics(MeterRegistry meters, BacktestRunRepository runs) {
        this.meters = meters;
        for (var status : RunStatus.values()) {
            meters.gauge("finvera.backtest.runs", List.of(Tag.of("status", status.name())),
                    runs, repository -> repository.countByStatus(status));
        }
        meters.gauge("finvera.backtest.queue.oldest.seconds", runs, repository -> {
            var oldest = repository.findOldestQueuedCreatedAt();
            return oldest == null ? 0 : Math.max(0, Duration.between(oldest, java.time.Instant.now()).toSeconds());
        });
        queueTime = meters.timer("finvera.backtest.queue");
        processedSessions = DistributionSummary.builder("finvera.backtest.processed.sessions").register(meters);
        attemptCount = DistributionSummary.builder("finvera.backtest.attempts").register(meters);
    }

    public void terminal(BacktestRunEntity run) {
        String reason = run.getReasonCode() == null ? "NONE" : run.getReasonCode();
        if (!OBSERVABLE_REASONS.contains(reason)) reason = "OTHER";
        meters.counter("finvera.backtest.terminal", "status", run.getStatus().name(), "reason", reason).increment();
        processedSessions.record(run.getProcessedSessions());
        attemptCount.record(run.getAttemptCount());
        if (run.getStartedAt() != null) {
            queueTime.record(Duration.between(run.getCreatedAt(), run.getStartedAt()));
        }
    }

    public void terminal(RunStatus status) {
        meters.counter("finvera.backtest.terminal", "status", status.name(), "reason", "NONE").increment();
    }

    public void recovery(String outcome) {
        String bounded = Set.of("REQUEUED", "FAILED").contains(outcome) ? outcome : "OTHER";
        meters.counter("finvera.backtest.recovery", "outcome", bounded).increment();
    }

    public Timer.Sample start() {
        return Timer.start(meters);
    }

    public void stop(Timer.Sample sample) {
        sample.stop(meters.timer("finvera.backtest.execution"));
    }
}

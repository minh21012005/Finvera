package com.minhnb.finvera_be.backtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.backtest.domain.BacktestTypes.RunStatus;
import com.minhnb.finvera_be.backtest.repository.BacktestRunRepository;
import com.minhnb.finvera_be.backtest.service.BacktestMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BacktestMetricsTests {
    @Test
    void exposesOnlyBoundedStatusTagsAndNoFinancialPayload() {
        var registry = new SimpleMeterRegistry();
        var runs = mock(BacktestRunRepository.class);
        when(runs.countByStatus(any())).thenReturn(0L);
        var metrics = new BacktestMetrics(registry, runs);

        for (var status : RunStatus.values()) metrics.terminal(status);
        metrics.recovery("REQUEUED");
        var sample = metrics.start();
        metrics.stop(sample);

        assertThat(registry.getMeters()).hasSize(16);
        assertThat(registry.find("finvera.backtest.queue.oldest.seconds").gauge()).isNotNull();
        assertThat(registry.find("finvera.backtest.queue").timer()).isNotNull();
        assertThat(registry.find("finvera.backtest.processed.sessions").summary()).isNotNull();
        assertThat(registry.find("finvera.backtest.attempts").summary()).isNotNull();
        assertThat(registry.getMeters()).allSatisfy(meter -> {
            assertThat(meter.getId().getName()).startsWith("finvera.backtest.");
            assertThat(meter.getId().getTags()).allSatisfy(tag -> {
                assertThat(Set.of("status", "reason", "outcome")).contains(tag.getKey());
                if (tag.getKey().equals("status")) {
                    assertThat(Set.of("QUEUED", "RUNNING", "COMPLETED", "WITHHELD", "FAILED"))
                            .contains(tag.getValue());
                }
                if (tag.getKey().equals("reason")) assertThat(tag.getValue()).isEqualTo("NONE");
                if (tag.getKey().equals("outcome")) assertThat(tag.getValue()).isEqualTo("REQUEUED");
            });
            assertThat(meter.getId().toString()).doesNotContain("FPT", "100000000", "owner", "symbol");
        });
    }
}

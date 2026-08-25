package com.minhnb.finvera_be.shared.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class DataRetentionCleanupServiceTests {

    @Test
    void cleanupRunsOnlyRetentionSafeDeletes() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        var service = new DataRetentionCleanupService(jdbc,
                Clock.fixed(Instant.parse("2026-08-26T00:00:00Z"), ZoneOffset.UTC));

        var result = service.cleanup(new DataRetentionCleanupProperties(30, 7, 252, 30));

        assertThat(result.auditsDeleted()).isEqualTo(1);
        assertThat(result.strategySignalsDeleted()).isEqualTo(1);
        assertThat(result.technicalResultsDeleted()).isEqualTo(1);
        assertThat(result.valuationsDeleted()).isEqualTo(1);
        assertThat(result.supersededDailyBarsDeleted()).isEqualTo(1);
        assertThat(result.regimesDeleted()).isEqualTo(1);
        assertThat(result.breadthSnapshotsDeleted()).isEqualTo(1);
        assertThat(result.liveEquityObservationsDeleted()).isEqualTo(1);
        assertThat(result.rejectedIngestionRecordsDeleted()).isEqualTo(1);
        assertThat(result.totalDeleted()).isEqualTo(9);
        verify(jdbc, times(23)).update(anyString(), any(Object[].class));
    }

    @Test
    void acceptedIngestionRecordsAndCurrentHistoricalBarsAreOutsideCleanupScope() {
        assertThat(DataRetentionCleanupService.DELETE_REJECTED_UNREFERENCED_INGESTION_RECORDS)
                .contains("record.status = 'REJECTED'")
                .doesNotContain("record.status = 'ACCEPTED'");

        assertThat(DataRetentionCleanupService.DELETE_OLD_DAILY_BARS)
                .contains("old_bar.is_current = false")
                .contains("breadth_snapshot_input")
                .contains("valuation_assessment_input")
                .contains("strategy_signal_input")
                .doesNotContain("old_bar.is_current = true");
    }
}

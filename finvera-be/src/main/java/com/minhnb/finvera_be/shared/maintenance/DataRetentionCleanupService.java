package com.minhnb.finvera_be.shared.maintenance;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owner-operated retention cleanup for derived/private operational data.
 *
 * <p>The service deliberately does not delete current historical price, index,
 * instrument, profile, sector, or fundamental facts. Those rows are the inputs
 * for technical indicators, valuation, regime assessment, charts, and future
 * rebuilds.
 *
 * <p>Not a component-scanned {@code @Service}: it is registered by
 * {@link DataRetentionCleanupConfiguration} behind the same
 * {@code finvera.data-retention.cleanup.enabled} gate as its runner, so a
 * context that never enables cleanup (including datasource-less test slices)
 * never demands the {@code JdbcTemplate} this class requires.
 */
public class DataRetentionCleanupService {

    static final String DELETE_SOURCE_RECONCILIATION_AUDIT = """
            DELETE FROM source_reconciliation_audit
            WHERE detected_at < ?
            """;

    static final String DELETE_REJECTED_UNREFERENCED_INGESTION_RECORDS = """
            DELETE FROM ingestion_record record
            WHERE record.status = 'REJECTED'
              AND record.ingested_at < ?
              AND NOT EXISTS (
                  SELECT 1 FROM ingestion_record newer
                  WHERE newer.supersedes_id = record.id
              )
              AND NOT EXISTS (
                  SELECT 1 FROM index_snapshot snapshot
                  WHERE snapshot.ingestion_record_id = record.id
              )
              AND NOT EXISTS (
                  SELECT 1 FROM equity_price_observation observation
                  WHERE observation.ingestion_record_id = record.id
              )
              AND NOT EXISTS (
                  SELECT 1 FROM equity_daily_bar bar
                  WHERE bar.ingestion_record_id = record.id
              )
              AND NOT EXISTS (
                  SELECT 1 FROM fundamental_report report
                  WHERE report.ingestion_record_id = record.id
              )
              AND NOT EXISTS (
                  SELECT 1 FROM corporate_action action
                  WHERE action.ingestion_record_id = record.id
              )
            """;

    static final String CLEAR_STRATEGY_SIGNAL_SUPERSEDES_LINKS = """
            UPDATE strategy_signal current_signal
            SET supersedes_id = NULL
            WHERE current_signal.supersedes_id IN (
                SELECT old_signal.id
                FROM strategy_signal old_signal
                WHERE old_signal.is_current = false
                  AND old_signal.calculated_at < ?
            )
            """;

    static final String DELETE_OLD_STRATEGY_SIGNAL_RISK_FACTORS = """
            DELETE FROM strategy_signal_risk_factor factor
            WHERE factor.signal_id IN (
                SELECT signal.id
                FROM strategy_signal signal
                WHERE signal.is_current = false
                  AND signal.calculated_at < ?
            )
            """;

    static final String DELETE_OLD_STRATEGY_SIGNAL_INPUTS = """
            DELETE FROM strategy_signal_input input
            WHERE input.signal_id IN (
                SELECT signal.id
                FROM strategy_signal signal
                WHERE signal.is_current = false
                  AND signal.calculated_at < ?
            )
            """;

    static final String DELETE_OLD_STRATEGY_SIGNALS = """
            DELETE FROM strategy_signal signal
            WHERE signal.is_current = false
              AND signal.calculated_at < ?
            """;

    static final String CLEAR_TECHNICAL_RESULT_SUPERSEDES_LINKS = """
            UPDATE technical_indicator_result current_result
            SET supersedes_id = NULL
            WHERE current_result.supersedes_id IN (
                SELECT old_result.id
                FROM technical_indicator_result old_result
                WHERE old_result.is_current = false
                  AND old_result.calculated_at < ?
            )
            """;

    static final String DELETE_OLD_TECHNICAL_VALUES = """
            DELETE FROM technical_indicator_value value
            WHERE value.result_id IN (
                SELECT result.id
                FROM technical_indicator_result result
                WHERE result.is_current = false
                  AND result.calculated_at < ?
                  AND NOT EXISTS (
                      SELECT 1 FROM strategy_signal_input input
                      WHERE input.technical_indicator_result_id = result.id
                  )
            )
            """;

    static final String DELETE_OLD_TECHNICAL_RESULTS = """
            DELETE FROM technical_indicator_result result
            WHERE result.is_current = false
              AND result.calculated_at < ?
              AND NOT EXISTS (
                  SELECT 1 FROM strategy_signal_input input
                  WHERE input.technical_indicator_result_id = result.id
              )
            """;

    static final String CLEAR_VALUATION_SUPERSEDES_LINKS = """
            UPDATE valuation_assessment current_assessment
            SET supersedes_id = NULL
            WHERE current_assessment.supersedes_id IN (
                SELECT old_assessment.id
                FROM valuation_assessment old_assessment
                WHERE old_assessment.is_current = false
                  AND old_assessment.calculated_at < ?
            )
            """;

    static final String DELETE_OLD_VALUATION_METRICS = """
            DELETE FROM valuation_metric metric
            WHERE metric.assessment_id IN (
                SELECT assessment.id
                FROM valuation_assessment assessment
                WHERE assessment.is_current = false
                  AND assessment.calculated_at < ?
            )
            """;

    static final String DELETE_OLD_VALUATION_INPUTS = """
            DELETE FROM valuation_assessment_input input
            WHERE input.assessment_id IN (
                SELECT assessment.id
                FROM valuation_assessment assessment
                WHERE assessment.is_current = false
                  AND assessment.calculated_at < ?
            )
            """;

    static final String DELETE_OLD_VALUATIONS = """
            DELETE FROM valuation_assessment assessment
            WHERE assessment.is_current = false
              AND assessment.calculated_at < ?
            """;

    static final String CLEAR_DAILY_BAR_SUPERSEDES_LINKS = """
            UPDATE equity_daily_bar current_bar
            SET supersedes_id = NULL
            WHERE current_bar.supersedes_id IN (
                SELECT old_bar.id
                FROM equity_daily_bar old_bar
                WHERE old_bar.is_current = false
                  AND old_bar.accepted_at < ?
            )
            """;

    static final String DELETE_OLD_DAILY_BARS = """
            DELETE FROM equity_daily_bar old_bar
            WHERE old_bar.is_current = false
              AND old_bar.accepted_at < ?
              AND NOT EXISTS (
                  SELECT 1 FROM valuation_assessment_input input
                  WHERE input.daily_bar_id = old_bar.id
              )
              AND NOT EXISTS (
                  SELECT 1 FROM strategy_signal_input input
                  WHERE input.daily_bar_id = old_bar.id
              )
              AND NOT EXISTS (
                  SELECT 1 FROM breadth_snapshot_input input
                  WHERE input.daily_bar_id = old_bar.id
              )
            """;

    static final String CLEAR_REGIME_SUPERSEDES_LINKS = """
            UPDATE regime_assessment current_assessment
            SET supersedes_id = NULL
            WHERE current_assessment.supersedes_id IN (
                SELECT old_assessment.id
                FROM regime_assessment old_assessment
                WHERE old_assessment.trading_date < ?
            )
            """;

    static final String DELETE_OLD_REGIME_FACTORS = """
            DELETE FROM regime_factor factor
            WHERE factor.assessment_id IN (
                SELECT assessment.id
                FROM regime_assessment assessment
                WHERE assessment.trading_date < ?
                  AND NOT EXISTS (
                      SELECT 1 FROM strategy_signal_input input
                      WHERE input.regime_assessment_id = assessment.id
                  )
            )
            """;

    static final String DELETE_OLD_REGIME_INPUTS = """
            DELETE FROM regime_assessment_input input
            WHERE input.assessment_id IN (
                SELECT assessment.id
                FROM regime_assessment assessment
                WHERE assessment.trading_date < ?
                  AND NOT EXISTS (
                      SELECT 1 FROM strategy_signal_input signal_input
                      WHERE signal_input.regime_assessment_id = assessment.id
                  )
            )
            """;

    static final String DELETE_OLD_REGIMES = """
            DELETE FROM regime_assessment assessment
            WHERE assessment.trading_date < ?
              AND NOT EXISTS (
                  SELECT 1 FROM strategy_signal_input input
                  WHERE input.regime_assessment_id = assessment.id
              )
            """;

    static final String CLEAR_BREADTH_SUPERSEDES_LINKS = """
            UPDATE breadth_snapshot current_snapshot
            SET supersedes_id = NULL
            WHERE current_snapshot.supersedes_id IN (
                SELECT old_snapshot.id
                FROM breadth_snapshot old_snapshot
                WHERE old_snapshot.trading_date < ?
            )
            """;

    static final String DELETE_OLD_BREADTH_INPUTS = """
            DELETE FROM breadth_snapshot_input input
            WHERE input.breadth_snapshot_id IN (
                SELECT snapshot.id
                FROM breadth_snapshot snapshot
                WHERE snapshot.trading_date < ?
                  AND NOT EXISTS (
                      SELECT 1 FROM regime_assessment_input regime_input
                      WHERE regime_input.breadth_snapshot_id = snapshot.id
                  )
            )
            """;

    static final String DELETE_OLD_BREADTH_SNAPSHOTS = """
            DELETE FROM breadth_snapshot snapshot
            WHERE snapshot.trading_date < ?
              AND NOT EXISTS (
                  SELECT 1 FROM regime_assessment_input input
                  WHERE input.breadth_snapshot_id = snapshot.id
              )
            """;

    static final String DELETE_OLD_LIVE_EQUITY_OBSERVATIONS = """
            DELETE FROM equity_price_observation observation
            WHERE observation.trading_date < ?
              AND NOT EXISTS (
                  SELECT 1 FROM breadth_snapshot_input input
                  WHERE input.price_observation_id = observation.id
              )
              AND NOT EXISTS (
                  SELECT 1 FROM regime_assessment_input input
                  WHERE input.price_observation_id = observation.id
              )
              AND NOT EXISTS (
                  SELECT 1 FROM valuation_assessment_input input
                  WHERE input.price_observation_id = observation.id
              )
            """;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public DataRetentionCleanupService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public CleanupResult cleanup(DataRetentionCleanupProperties properties) {
        Instant now = clock.instant();
        Timestamp auditCutoff = Timestamp.from(now.minusSeconds(daysToSeconds(properties.auditRetentionDays())));
        Timestamp revisionCutoff = Timestamp.from(now.minusSeconds(daysToSeconds(properties.revisionRetentionDays())));
        LocalDate liveCutoff = LocalDate.now(clock).minusDays(properties.liveRetentionDays());
        LocalDate eodCutoff = LocalDate.now(clock).minusDays(properties.eodCalculationRetentionDays());

        int auditsDeleted = jdbc.update(DELETE_SOURCE_RECONCILIATION_AUDIT, auditCutoff);
        int strategySignalsDeleted = pruneStrategySignals(revisionCutoff);
        int technicalResultsDeleted = pruneTechnicalResults(revisionCutoff);
        int valuationsDeleted = pruneValuations(revisionCutoff);
        int supersededDailyBarsDeleted = pruneSupersededDailyBars(revisionCutoff);
        int regimesDeleted = pruneOldRegimes(eodCutoff);
        int breadthSnapshotsDeleted = pruneOldBreadthSnapshots(eodCutoff);
        int liveEquityObservationsDeleted = jdbc.update(DELETE_OLD_LIVE_EQUITY_OBSERVATIONS, liveCutoff);
        int rejectedIngestionRecordsDeleted = jdbc.update(DELETE_REJECTED_UNREFERENCED_INGESTION_RECORDS, auditCutoff);

        return new CleanupResult(auditsDeleted, strategySignalsDeleted, technicalResultsDeleted,
                valuationsDeleted, supersededDailyBarsDeleted, regimesDeleted, breadthSnapshotsDeleted,
                liveEquityObservationsDeleted, rejectedIngestionRecordsDeleted);
    }

    private int pruneStrategySignals(Timestamp cutoff) {
        jdbc.update(CLEAR_STRATEGY_SIGNAL_SUPERSEDES_LINKS, cutoff);
        jdbc.update(DELETE_OLD_STRATEGY_SIGNAL_RISK_FACTORS, cutoff);
        jdbc.update(DELETE_OLD_STRATEGY_SIGNAL_INPUTS, cutoff);
        return jdbc.update(DELETE_OLD_STRATEGY_SIGNALS, cutoff);
    }

    private int pruneTechnicalResults(Timestamp cutoff) {
        jdbc.update(CLEAR_TECHNICAL_RESULT_SUPERSEDES_LINKS, cutoff);
        jdbc.update(DELETE_OLD_TECHNICAL_VALUES, cutoff);
        return jdbc.update(DELETE_OLD_TECHNICAL_RESULTS, cutoff);
    }

    private int pruneValuations(Timestamp cutoff) {
        jdbc.update(CLEAR_VALUATION_SUPERSEDES_LINKS, cutoff);
        jdbc.update(DELETE_OLD_VALUATION_METRICS, cutoff);
        jdbc.update(DELETE_OLD_VALUATION_INPUTS, cutoff);
        return jdbc.update(DELETE_OLD_VALUATIONS, cutoff);
    }

    private int pruneSupersededDailyBars(Timestamp cutoff) {
        jdbc.update(CLEAR_DAILY_BAR_SUPERSEDES_LINKS, cutoff);
        return jdbc.update(DELETE_OLD_DAILY_BARS, cutoff);
    }

    private int pruneOldRegimes(LocalDate cutoff) {
        jdbc.update(CLEAR_REGIME_SUPERSEDES_LINKS, cutoff);
        jdbc.update(DELETE_OLD_REGIME_FACTORS, cutoff);
        jdbc.update(DELETE_OLD_REGIME_INPUTS, cutoff);
        return jdbc.update(DELETE_OLD_REGIMES, cutoff);
    }

    private int pruneOldBreadthSnapshots(LocalDate cutoff) {
        jdbc.update(CLEAR_BREADTH_SUPERSEDES_LINKS, cutoff);
        jdbc.update(DELETE_OLD_BREADTH_INPUTS, cutoff);
        return jdbc.update(DELETE_OLD_BREADTH_SNAPSHOTS, cutoff);
    }

    private static long daysToSeconds(int days) {
        return days * 86_400L;
    }

    public record CleanupResult(
            int auditsDeleted,
            int strategySignalsDeleted,
            int technicalResultsDeleted,
            int valuationsDeleted,
            int supersededDailyBarsDeleted,
            int regimesDeleted,
            int breadthSnapshotsDeleted,
            int liveEquityObservationsDeleted,
            int rejectedIngestionRecordsDeleted) {

        public int totalDeleted() {
            return auditsDeleted + strategySignalsDeleted + technicalResultsDeleted
                    + valuationsDeleted + supersededDailyBarsDeleted + regimesDeleted
                    + breadthSnapshotsDeleted + liveEquityObservationsDeleted
                    + rejectedIngestionRecordsDeleted;
        }
    }
}

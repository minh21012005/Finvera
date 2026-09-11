package com.minhnb.finvera_be.stock.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class DebtToEquityNormalizationMigrationTests {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Test
    void v020ConvertsVciReportAndMaterializedSummaryButLeavesKbsPercentPointsUnchanged() throws Exception {
        Flyway flywayToV019 = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .target("19")
                .load();
        flywayToV019.migrate();

        UUID instrumentId = UUID.randomUUID();
        UUID vciReportId = UUID.randomUUID();
        UUID alreadyNormalizedVciReportId = UUID.randomUUID();
        UUID kbsReportId = UUID.randomUUID();
        UUID summaryId = UUID.randomUUID();

        try (Connection connection = connection()) {
            insertInstrument(connection, instrumentId);
            insertReport(connection, instrumentId, vciReportId, "VNSTOCK_VCI", "1.25", "vci-debt-to-equity-v1");
            insertReport(connection, instrumentId, alreadyNormalizedVciReportId, "VNSTOCK_VCI", "125", "vci-debt-to-equity-percent-v2");
            insertReport(connection, instrumentId, kbsReportId, "VNSTOCK_KBS", "125", "PROVIDER_REPORTED");
            insertSummary(connection, instrumentId, summaryId, vciReportId, "1.25", "vci-debt-to-equity-v1");
        }

        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = connection()) {
            assertThat(reportMetric(connection, vciReportId)).isEqualByComparingTo("125");
            assertThat(reportReason(connection, vciReportId)).isEqualTo("vci-debt-to-equity-percent-v2");
            assertThat(reportMetric(connection, alreadyNormalizedVciReportId)).isEqualByComparingTo("125");
            assertThat(reportMetric(connection, kbsReportId)).isEqualByComparingTo("125");
            assertThat(summaryMetric(connection, summaryId)).isEqualByComparingTo("125");
            assertThat(summaryReason(connection, summaryId)).isEqualTo("vci-debt-to-equity-percent-v2");
        }
    }

    private static void insertInstrument(Connection connection, UUID instrumentId) throws Exception {
        try (var statement = connection.prepareStatement("""
                insert into market_instrument
                    (id, venue, symbol, instrument_type, listed_from, status, source, source_revision)
                values (?, 'HOSE', 'D2E', 'EQUITY', '2020-01-01', 'ACTIVE', 'FINVERA_FIXTURE', 'v1')
                """)) {
            statement.setObject(1, instrumentId);
            statement.executeUpdate();
        }
    }

    private static void insertReport(
            Connection connection, UUID instrumentId, UUID reportId, String source, String debtToEquity, String derivation)
            throws Exception {
        UUID ingestionId = UUID.randomUUID();
        try (var statement = connection.prepareStatement("""
                insert into ingestion_record
                    (id, source, dataset, subject_key, trading_date, observed_at, ingested_at, payload_hash, status)
                values (?, ?, 'FUNDAMENTALS', ?, '2025-12-31', now(), now(), ?, 'ACCEPTED')
                """)) {
            statement.setObject(1, ingestionId);
            statement.setString(2, source);
            statement.setString(3, source + "|D2E|2025");
            statement.setString(4, source.equals("VNSTOCK_VCI") ? "a".repeat(64) : "b".repeat(64));
            statement.executeUpdate();
        }
        try (var statement = connection.prepareStatement("""
                insert into fundamental_report
                    (id, instrument_id, ingestion_record_id, period_type, fiscal_year,
                     period_start, period_end, report_kind, audit_status, currency, unit_scale,
                     catalog_version, observed_at, accepted_at, source, revision)
                values (?, ?, ?, 'ANNUAL', 2025, '2025-01-01', '2025-12-31', 'CONSOLIDATED',
                        'AUDITED', 'VND', 1, 'fundamental-metric-catalog-v1', now(), now(), ?, 1)
                """)) {
            statement.setObject(1, reportId);
            statement.setObject(2, instrumentId);
            statement.setObject(3, ingestionId);
            statement.setString(4, source);
            statement.executeUpdate();
        }
        try (var statement = connection.prepareStatement("""
                insert into fundamental_report_metric (report_id, metric_code, value, applicability, quality_reason)
                values (?, 'DEBT_TO_EQUITY', ?, 'DEFINED', ?)
                """)) {
            statement.setObject(1, reportId);
            statement.setBigDecimal(2, new BigDecimal(debtToEquity));
            statement.setString(3, derivation);
            statement.executeUpdate();
        }
    }

    private static void insertSummary(
            Connection connection, UUID instrumentId, UUID summaryId, UUID reportId, String debtToEquity, String derivation)
            throws Exception {
        try (var statement = connection.prepareStatement("""
                insert into fundamental_summary
                    (id, instrument_id, as_of_trading_date, rule_version, basis_period_end,
                     basis_period_label, data_status, calculated_at)
                values (?, ?, '2026-01-02', 'fundamental-v1', '2025-12-31', 'FY2025', 'CURRENT', now())
                """)) {
            statement.setObject(1, summaryId);
            statement.setObject(2, instrumentId);
            statement.executeUpdate();
        }
        try (var statement = connection.prepareStatement("""
                insert into fundamental_summary_input (summary_id, input_role, report_id)
                values (?, 'CONTRIBUTING_REPORT_0', ?)
                """)) {
            statement.setObject(1, summaryId);
            statement.setObject(2, reportId);
            statement.executeUpdate();
        }
        try (var statement = connection.prepareStatement("""
                insert into fundamental_summary_metric (summary_id, metric_code, value, applicability, quality_reason)
                values (?, 'DEBT_TO_EQUITY', ?, 'DEFINED', ?)
                """)) {
            statement.setObject(1, summaryId);
            statement.setBigDecimal(2, new BigDecimal(debtToEquity));
            statement.setString(3, derivation);
            statement.executeUpdate();
        }
    }

    private static BigDecimal reportMetric(Connection connection, UUID reportId) throws Exception {
        try (var statement = connection.prepareStatement("""
                select value from fundamental_report_metric
                 where report_id = ? and metric_code = 'DEBT_TO_EQUITY'
                """)) {
            statement.setObject(1, reportId);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getBigDecimal(1);
            }
        }
    }

    private static BigDecimal summaryMetric(Connection connection, UUID summaryId) throws Exception {
        try (var statement = connection.prepareStatement("""
                select value from fundamental_summary_metric
                 where summary_id = ? and metric_code = 'DEBT_TO_EQUITY'
                """)) {
            statement.setObject(1, summaryId);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getBigDecimal(1);
            }
        }
    }

    private static String reportReason(Connection connection, UUID reportId) throws Exception {
        try (var statement = connection.prepareStatement("""
                select quality_reason from fundamental_report_metric
                 where report_id = ? and metric_code = 'DEBT_TO_EQUITY'
                """)) {
            statement.setObject(1, reportId);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getString(1);
            }
        }
    }

    private static String summaryReason(Connection connection, UUID summaryId) throws Exception {
        try (var statement = connection.prepareStatement("""
                select quality_reason from fundamental_summary_metric
                 where summary_id = ? and metric_code = 'DEBT_TO_EQUITY'
                """)) {
            statement.setObject(1, summaryId);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getString(1);
            }
        }
    }

    private static Connection connection() throws Exception {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}

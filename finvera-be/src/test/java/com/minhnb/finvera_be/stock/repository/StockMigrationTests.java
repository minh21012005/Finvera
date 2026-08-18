package com.minhnb.finvera_be.stock.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class StockMigrationTests {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @BeforeAll
    static void migrateFromEmpty() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @Test
    void createsAllFifteenStockTablesAndSeedsTheMetricCatalog() throws Exception {
        try (var connection = connection();
                var tables = connection.prepareStatement("""
                        select count(*) from information_schema.tables
                        where table_schema = 'public' and table_name in (
                          'sector_reference', 'equity_profile', 'equity_daily_bar',
                          'corporate_action', 'fundamental_metric_catalog', 'fundamental_report',
                          'fundamental_report_metric', 'fundamental_summary', 'fundamental_summary_metric',
                          'fundamental_summary_input', 'technical_indicator_result',
                          'technical_indicator_value', 'valuation_assessment', 'valuation_metric',
                          'valuation_assessment_input')
                        """);
                var catalog = connection.prepareStatement(
                        "select count(*) from fundamental_metric_catalog where catalog_version = 'fundamental-metric-catalog-v1'")) {
            try (var result = tables.executeQuery()) {
                result.next();
                assertThat(result.getInt(1)).isEqualTo(15);
            }
            try (var result = catalog.executeQuery()) {
                result.next();
                assertThat(result.getInt(1)).isEqualTo(15);
            }
        }
    }

    @Test
    void preservesExactDecimalsAndUtcInstantsOnDailyBar() throws Exception {
        UUID instrumentId = insertInstrument("FPT");
        UUID ingestionId = insertAcceptedIngestion("DAILY_BAR", "FPT|2026-08-14");
        UUID barId = UUID.randomUUID();
        try (var connection = connection();
                var insert = connection.prepareStatement("""
                        insert into equity_daily_bar (
                          id, instrument_id, ingestion_record_id, trading_date,
                          open_price, high_price, low_price, close_price,
                          adjustment_status, volume, source, observed_at, accepted_at, revision)
                        values (?, ?, ?, '2026-08-14', ?, ?, ?, ?, 'RAW', ?, 'FINVERA_FIXTURE', ?, ?, 1)
                        """)) {
            insert.setObject(1, barId);
            insert.setObject(2, instrumentId);
            insert.setObject(3, ingestionId);
            insert.setBigDecimal(4, new BigDecimal("122500.000000"));
            insert.setBigDecimal(5, new BigDecimal("123800.000000"));
            insert.setBigDecimal(6, new BigDecimal("122300.000000"));
            insert.setBigDecimal(7, new BigDecimal("123600.000000"));
            insert.setLong(8, 2_270_000L);
            insert.setObject(9, OffsetDateTime.of(2026, 8, 14, 8, 15, 0, 0, ZoneOffset.UTC));
            insert.setObject(10, OffsetDateTime.of(2026, 8, 14, 8, 15, 1, 0, ZoneOffset.UTC));
            insert.executeUpdate();
        }
        try (var connection = connection();
                var query = connection.prepareStatement(
                        "select close_price, observed_at from equity_daily_bar where id = ?")) {
            query.setObject(1, barId);
            try (var result = query.executeQuery()) {
                result.next();
                assertThat(result.getBigDecimal(1)).isEqualByComparingTo("123600.000000");
                assertThat(result.getObject(2, OffsetDateTime.class).getOffset()).isEqualTo(ZoneOffset.UTC);
            }
        }
    }

    @Test
    void rejectsInvertedOhlcOrdering() throws Exception {
        UUID instrumentId = insertInstrument("BADOHLC");
        UUID ingestionId = insertAcceptedIngestion("DAILY_BAR", "BADOHLC|2026-08-14");
        try (var connection = connection();
                var insert = connection.prepareStatement("""
                        insert into equity_daily_bar (
                          id, instrument_id, ingestion_record_id, trading_date,
                          open_price, high_price, low_price, close_price,
                          adjustment_status, source, observed_at, accepted_at, revision)
                        values (?, ?, ?, '2026-08-14', ?, ?, ?, ?, 'RAW', 'FINVERA_FIXTURE', now(), now(), 1)
                        """)) {
            insert.setObject(1, UUID.randomUUID());
            insert.setObject(2, instrumentId);
            insert.setObject(3, ingestionId);
            insert.setBigDecimal(4, new BigDecimal("100.000000"));
            // high below low: must be rejected by the OHLC ordering check.
            insert.setBigDecimal(5, new BigDecimal("90.000000"));
            insert.setBigDecimal(6, new BigDecimal("95.000000"));
            insert.setBigDecimal(7, new BigDecimal("92.000000"));
            assertThatThrownBy(insert::executeUpdate).isInstanceOf(SQLException.class);
        }
    }

    @Test
    void rejectsAHalfPublishedValuationAssessment() throws Exception {
        UUID instrumentId = insertInstrument("HALFPUB");
        try (var connection = connection();
                var insert = connection.prepareStatement("""
                        insert into valuation_assessment (
                          id, instrument_id, as_of_trading_date, as_of, rule_version,
                          classification, score, displayed_score, confidence,
                          used_own_history, used_sector, data_status, reason_codes, calculated_at)
                        values (?, ?, '2026-08-14', now(), 'valuation-v1',
                          'OVER_VALUED', null, 80, 70, true, false, 'CURRENT', '{}', now())
                        """)) {
            insert.setObject(1, UUID.randomUUID());
            insert.setObject(2, instrumentId);
            assertThatThrownBy(insert::executeUpdate).isInstanceOf(SQLException.class);
        }
    }

    @Test
    void enforcesOneCurrentRevisionPerDailyBarKey() throws Exception {
        UUID instrumentId = insertInstrument("DUPCUR");
        UUID firstIngestion = insertAcceptedIngestion("DAILY_BAR", "DUPCUR|2026-08-14|1");
        UUID secondIngestion = insertAcceptedIngestion("DAILY_BAR", "DUPCUR|2026-08-14|2");
        insertCurrentDailyBar(instrumentId, firstIngestion, "2026-08-14");
        try (var connection = connection();
                var insert = connection.prepareStatement("""
                        insert into equity_daily_bar (
                          id, instrument_id, ingestion_record_id, trading_date,
                          open_price, high_price, low_price, close_price,
                          adjustment_status, source, observed_at, accepted_at, revision, is_current)
                        values (?, ?, ?, '2026-08-14', ?, ?, ?, ?, 'RAW', 'FINVERA_FIXTURE', now(), now(), 2, true)
                        """)) {
            insert.setObject(1, UUID.randomUUID());
            insert.setObject(2, instrumentId);
            insert.setObject(3, secondIngestion);
            insert.setBigDecimal(4, new BigDecimal("100.000000"));
            insert.setBigDecimal(5, new BigDecimal("101.000000"));
            insert.setBigDecimal(6, new BigDecimal("99.000000"));
            insert.setBigDecimal(7, new BigDecimal("100.500000"));
            assertThatThrownBy(insert::executeUpdate).isInstanceOf(SQLException.class);
        }
    }

    private static void insertCurrentDailyBar(UUID instrumentId, UUID ingestionId, String tradingDate)
            throws Exception {
        try (var connection = connection();
                var insert = connection.prepareStatement("""
                        insert into equity_daily_bar (
                          id, instrument_id, ingestion_record_id, trading_date,
                          open_price, high_price, low_price, close_price,
                          adjustment_status, source, observed_at, accepted_at, revision, is_current)
                        values (?, ?, ?, ?::date, ?, ?, ?, ?, 'RAW', 'FINVERA_FIXTURE', now(), now(), 1, true)
                        """)) {
            insert.setObject(1, UUID.randomUUID());
            insert.setObject(2, instrumentId);
            insert.setObject(3, ingestionId);
            insert.setString(4, tradingDate);
            insert.setBigDecimal(5, new BigDecimal("100.000000"));
            insert.setBigDecimal(6, new BigDecimal("101.000000"));
            insert.setBigDecimal(7, new BigDecimal("99.000000"));
            insert.setBigDecimal(8, new BigDecimal("100.500000"));
            insert.executeUpdate();
        }
    }

    private static UUID insertInstrument(String symbol) throws Exception {
        UUID id = UUID.randomUUID();
        try (var connection = connection();
                var insert = connection.prepareStatement("""
                        insert into market_instrument (
                          id, venue, symbol, instrument_type, listed_from, status, source, source_revision)
                        values (?, 'HOSE', ?, 'EQUITY', '2010-01-01', 'ACTIVE', 'FINVERA_FIXTURE', 'v1')
                        """)) {
            insert.setObject(1, id);
            insert.setString(2, symbol);
            insert.executeUpdate();
        }
        return id;
    }

    private static UUID insertAcceptedIngestion(String dataset, String subject) throws Exception {
        UUID id = UUID.randomUUID();
        try (var connection = connection();
                var insert = connection.prepareStatement("""
                        insert into ingestion_record (
                          id, source, dataset, subject_key, trading_date, observed_at,
                          ingested_at, payload_hash, status)
                        values (?, 'FINVERA_FIXTURE', ?, ?, '2026-08-14',
                          '2026-08-14T08:15:00Z', '2026-08-14T08:15:01Z', ?, 'ACCEPTED')
                        """)) {
            insert.setObject(1, id);
            insert.setString(2, dataset);
            insert.setString(3, subject);
            insert.setString(4, String.format("%064x", subject.hashCode() & 0xffffffffL));
            insert.executeUpdate();
        }
        return id;
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}

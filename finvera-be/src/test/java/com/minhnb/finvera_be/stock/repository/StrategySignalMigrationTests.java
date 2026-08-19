package com.minhnb.finvera_be.stock.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class StrategySignalMigrationTests {

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
    void createsAllThreeStrategySignalTables() throws Exception {
        try (var connection = connection();
                var tables = connection.prepareStatement("""
                        select count(*) from information_schema.tables
                        where table_schema = 'public' and table_name in
                          ('strategy_signal', 'strategy_signal_risk_factor', 'strategy_signal_input')
                        """);
                var result = tables.executeQuery()) {
            result.next();
            assertThat(result.getInt(1)).isEqualTo(3);
        }
    }

    @Test
    void acceptsAFullyPublishedSignal() throws Exception {
        UUID instrumentId = insertInstrument("SIGOK");
        UUID signalId = UUID.randomUUID();
        try (var connection = connection()) {
            insertSignal(connection, signalId, instrumentId, "TREND_FOLLOWING", "2026-08-14",
                    "95.0", "105.0", "80.0", "120.0", "140.0", (short) 40, "MEDIUM", true);
            try (var query = connection.prepareStatement("select risk_level from strategy_signal where id = ?")) {
                query.setObject(1, signalId);
                try (var rs = query.executeQuery()) {
                    rs.next();
                    assertThat(rs.getString(1)).isEqualTo("MEDIUM");
                }
            }
        }
    }

    @Test
    void rejectsARiskScoreWithoutAMatchingRiskLevel() throws Exception {
        UUID instrumentId = insertInstrument("HALFRISK");
        try (var connection = connection()) {
            assertThatThrownBy(() -> insertSignal(connection, UUID.randomUUID(), instrumentId, "MOMENTUM",
                    "2026-08-14", "95.0", "105.0", "80.0", "120.0", "140.0", (short) 40, null, true))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void rejectsEntryLowAboveEntryHigh() throws Exception {
        UUID instrumentId = insertInstrument("BADENTRY");
        try (var connection = connection()) {
            assertThatThrownBy(() -> insertSignal(connection, UUID.randomUUID(), instrumentId, "BREAKOUT",
                    "2026-08-14", "106.0", "105.0", "80.0", "120.0", "140.0", null, null, true))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void rejectsTarget1NotBelowTarget2() throws Exception {
        UUID instrumentId = insertInstrument("BADTARGET");
        try (var connection = connection()) {
            assertThatThrownBy(() -> insertSignal(connection, UUID.randomUUID(), instrumentId, "PULLBACK",
                    "2026-08-14", "95.0", "105.0", "80.0", "140.0", "120.0", null, null, true))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void enforcesOneCurrentRevisionPerSignalKey() throws Exception {
        UUID instrumentId = insertInstrument("DUPSIGNAL");
        try (var connection = connection()) {
            insertSignal(connection, UUID.randomUUID(), instrumentId, "RSI_BASED", "2026-08-14",
                    "95.0", "105.0", "80.0", "120.0", "140.0", null, null, true);
            assertThatThrownBy(() -> insertSignal(connection, UUID.randomUUID(), instrumentId, "RSI_BASED",
                    "2026-08-14", "96.0", "106.0", "81.0", "121.0", "141.0", null, null, true))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void rejectsADefinedRiskFactorWithoutAScore() throws Exception {
        UUID instrumentId = insertInstrument("BADFACTOR");
        UUID signalId = UUID.randomUUID();
        try (var connection = connection()) {
            insertSignal(connection, signalId, instrumentId, "MACD_BASED", "2026-08-14",
                    "95.0", "105.0", "80.0", "120.0", "140.0", null, null, true);
            try (var insert = connection.prepareStatement("""
                    insert into strategy_signal_risk_factor
                      (signal_id, factor_code, input_value, factor_score, applicability)
                    values (?, 'VOLATILITY', 5.0, null, 'DEFINED')
                    """)) {
                insert.setObject(1, signalId);
                assertThatThrownBy(insert::executeUpdate).isInstanceOf(SQLException.class);
            }
        }
    }

    @Test
    void acceptsAThreeStateRiskFactorSet() throws Exception {
        UUID instrumentId = insertInstrument("THREESTATE");
        UUID signalId = UUID.randomUUID();
        try (var connection = connection()) {
            insertSignal(connection, signalId, instrumentId, "MEAN_REVERSION", "2026-08-14",
                    "95.0", "105.0", "80.0", "120.0", "140.0", (short) 20, "LOW", true);
            try (var insert = connection.prepareStatement("""
                    insert into strategy_signal_risk_factor
                      (signal_id, factor_code, input_value, factor_score, applicability, quality_reason)
                    values (?, 'MARKET_REGIME', null, null, 'MISSING', 'REGIME_UNAVAILABLE')
                    """)) {
                insert.setObject(1, signalId);
                insert.executeUpdate();
            }
            try (var query = connection.prepareStatement(
                    "select applicability from strategy_signal_risk_factor where signal_id = ?")) {
                query.setObject(1, signalId);
                try (var rs = query.executeQuery()) {
                    rs.next();
                    assertThat(rs.getString(1)).isEqualTo("MISSING");
                }
            }
        }
    }

    private static void insertSignal(Connection connection, UUID signalId, UUID instrumentId, String strategyCode,
            String asOfTradingDate, String entryLow, String entryHigh, String stopLoss, String target1,
            String target2, Short riskScore, String riskLevel, boolean current) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                insert into strategy_signal (
                  id, instrument_id, strategy_code, rule_version, as_of_trading_date, direction,
                  entry_low, entry_high, stop_loss, target1, target2, risk_reward,
                  risk_score, risk_level, input_set_hash, calculated_at, is_current)
                values (?, ?, ?, 'strategy-signal-v1', ?::date, 'LONG',
                  ?, ?, ?, ?, ?, 2.0000, ?, ?, ?, now(), ?)
                """)) {
            insert.setObject(1, signalId);
            insert.setObject(2, instrumentId);
            insert.setString(3, strategyCode);
            insert.setString(4, asOfTradingDate);
            insert.setBigDecimal(5, new BigDecimal(entryLow));
            insert.setBigDecimal(6, new BigDecimal(entryHigh));
            insert.setBigDecimal(7, new BigDecimal(stopLoss));
            insert.setBigDecimal(8, new BigDecimal(target1));
            insert.setBigDecimal(9, new BigDecimal(target2));
            if (riskScore == null) {
                insert.setNull(10, java.sql.Types.SMALLINT);
            } else {
                insert.setShort(10, riskScore);
            }
            insert.setString(11, riskLevel);
            insert.setString(12, "a".repeat(64));
            insert.setBoolean(13, current);
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

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}

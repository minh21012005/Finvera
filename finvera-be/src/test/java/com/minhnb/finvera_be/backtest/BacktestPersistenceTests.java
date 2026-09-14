package com.minhnb.finvera_be.backtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class BacktestPersistenceTests {
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @BeforeAll static void migrate() {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").load().migrate();
    }

    @Test void createsRunAndFiveResultTables() throws Exception {
        try (Connection c = connection(); var q = c.prepareStatement("""
                select count(*) from information_schema.tables where table_schema='public' and table_name in
                ('backtest_run','backtest_trade','backtest_equity_point','backtest_metric','backtest_entry_event','backtest_evidence')
                """); var rs = q.executeQuery()) {
            rs.next(); assertThat(rs.getInt(1)).isEqualTo(6);
        }
    }

    @Test void enforcesOwnerIdempotencyAndTerminalConsistency() throws Exception {
        UUID owner = UUID.randomUUID();
        insertRun(UUID.randomUUID(), owner, "same-key", "QUEUED", null, null);
        assertThatThrownBy(() -> insertRun(UUID.randomUUID(), owner, "same-key", "QUEUED", null, null))
                .isInstanceOf(SQLException.class);
        insertRun(UUID.randomUUID(), UUID.randomUUID(), "same-key", "QUEUED", null, null);
        assertThatThrownBy(() -> insertRun(UUID.randomUUID(), owner, null, "COMPLETED", null, null))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> insertRun(UUID.randomUUID(), owner, null, "WITHHELD", null, null))
                .isInstanceOf(SQLException.class);
        insertRun(UUID.randomUUID(), owner, null, "FAILED", null, "BROKEN");
    }

    @Test void enforcesChildUniquenessAndEquityIdentity() throws Exception {
        UUID run = UUID.randomUUID(); insertRun(run, UUID.randomUUID(), null, "QUEUED", null, null);
        try (Connection c = connection(); var p = c.prepareStatement("""
                insert into backtest_equity_point(id,run_id,trading_date,cash_vnd,open_position_value_vnd,total_equity_vnd,open_tranche_count,daily_bar_id)
                values (?,?,date '2026-01-02',100,20,120,1,?)
                """)) {
            p.setObject(1, UUID.randomUUID()); p.setObject(2, run); p.setObject(3, UUID.randomUUID()); p.executeUpdate();
        }
        assertThatThrownBy(() -> insertEquity(run, "2026-01-02", "120" )).isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> insertEquity(run, "2026-01-03", "119" )).isInstanceOf(SQLException.class);
    }

    private static void insertRun(UUID id, UUID owner, String key, String status, String fingerprint,
            String reason) throws Exception {
        try (Connection c = connection(); var p = c.prepareStatement("""
                insert into backtest_run(id,owner_id,idempotency_key,status,strategy_code,strategy_rule_version,
                symbol,instrument_id,venue,reporting_start,reporting_end,initial_capital_vnd,risk_per_tranche_rate,
                max_aggregate_open_risk_rate,costs_excluded,entry_fee_rate,exit_fee_rate,sell_tax_rate,
                entry_slippage_rate,exit_slippage_rate,engine_rule_version,metrics_rule_version,
                pyramiding_rule_version,sizing_rule_version,lot_rule_version,data_cutoff_accepted_at,
                input_fingerprint,reason_code,created_at,completed_at)
                values (?,?,?,?,?,'strategy-signal-v1','FPT',?,'HOSE',date '2025-01-01',date '2026-01-01',
                100000000,0.005,0.02,false,0.001,0.001,0.001,0.001,0.001,'backtest-engine-v1',
                'backtest-metrics-v1','pyramiding-v1','position-sizing-v1','market-lot-v1',now(),?,?,now(),
                case when ? in ('COMPLETED','WITHHELD','FAILED') then now() else null end)
                """)) {
            p.setObject(1,id); p.setObject(2,owner); p.setString(3,key); p.setString(4,status);
            p.setString(5,"MA_CROSSOVER"); p.setObject(6,UUID.randomUUID()); p.setString(7,fingerprint);
            p.setString(8,reason); p.setString(9,status); p.executeUpdate();
        }
    }

    private static void insertEquity(UUID run, String date, String total) throws Exception {
        try (Connection c=connection(); var p=c.prepareStatement("""
                insert into backtest_equity_point(id,run_id,trading_date,cash_vnd,open_position_value_vnd,total_equity_vnd,open_tranche_count,daily_bar_id)
                values (?,?,?::date,100,20,?,1,?)
                """)) {
            p.setObject(1,UUID.randomUUID()); p.setObject(2,run); p.setString(3,date);
            p.setBigDecimal(4,new java.math.BigDecimal(total)); p.setObject(5,UUID.randomUUID()); p.executeUpdate();
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}

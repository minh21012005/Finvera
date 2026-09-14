package com.minhnb.finvera_be.backtest.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.backtest.domain.BacktestTypes.Assumptions;
import com.minhnb.finvera_be.backtest.domain.BacktestTypes.Costs;
import com.minhnb.finvera_be.backtest.domain.BacktestTypes.SessionBar;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BacktestDeterminismTests {
    @Test
    void oneHundredFrozenInputRunsProduceIdenticalFinancialOutputsAndVersions() {
        var sessions = frozenSessions();
        var assumptions = new Assumptions(bd("100000000"), bd("0.01"), bd("0.04"),
                new Costs(bd("0.001"), bd("0.001"), bd("0.001"), bd("0.0005"), bd("0.0005"), false));
        var expected = snapshot(sessions, assumptions);

        for (int run = 0; run < 100; run++) {
            assertThat(snapshot(sessions, assumptions)).isEqualTo(expected);
        }
    }

    private static FrozenOutput snapshot(List<BacktestEngineV1.Session> sessions, Assumptions assumptions) {
        var result = BacktestEngineV1.run(sessions, assumptions, 100);
        var metrics = BacktestMetricsV1.calculate(assumptions.initialCapitalVnd(), result.equity(), result.trades());
        return new FrozenOutput(result.trades(), result.equity(), result.events(), metrics,
                BacktestEngineV1.RULE_VERSION, BacktestMetricsV1.RULE_VERSION, "a".repeat(64));
    }

    private static List<BacktestEngineV1.Session> frozenSessions() {
        Instant acceptedAt = Instant.parse("2026-01-02T09:00:00Z");
        return List.of(
                session("00000000-0000-0000-0000-000000000001", "2026-01-02", "100", "101", "99", "100",
                        acceptedAt, new BacktestEngineV1.Signal(true, bd("90"), bd("120"), bd("5"))),
                session("00000000-0000-0000-0000-000000000002", "2026-01-05", "101", "105", "98", "103",
                        acceptedAt.plusSeconds(86_400), new BacktestEngineV1.Signal(false, null, null, null)),
                session("00000000-0000-0000-0000-000000000003", "2026-01-06", "104", "121", "102", "119",
                        acceptedAt.plusSeconds(172_800), new BacktestEngineV1.Signal(false, null, null, null)));
    }

    private static BacktestEngineV1.Session session(String id, String date, String open, String high, String low,
            String close, Instant acceptedAt, BacktestEngineV1.Signal signal) {
        var bar = new SessionBar(UUID.fromString(id), LocalDate.parse(date), bd(open), bd(high), bd(low), bd(close),
                BigDecimal.ONE, "RAW", "FROZEN_FIXTURE", acceptedAt, acceptedAt);
        return new BacktestEngineV1.Session(bar, true, signal);
    }

    private record FrozenOutput(List<BacktestEngineV1.Trade> trades, List<BacktestEngineV1.Equity> equity,
            List<BacktestEngineV1.Event> events, List<BacktestMetricsV1.Metric> metrics,
            String engineVersion, String metricVersion, String evidenceFingerprint) {
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}

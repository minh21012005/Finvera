package com.minhnb.finvera_be.backtest.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.backtest.domain.BacktestTypes.MetricCode;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BacktestMetricsV1Tests {
    @Test
    void noTradeMetricsAreExplicitlyUnavailable() {
        var points = List.of(equity("2026-01-02", "100"), equity("2026-01-05", "100"));
        var metrics = BacktestMetricsV1.calculate(decimal("100"), points, List.of());

        assertThat(metric(metrics, MetricCode.TOTAL_RETURN).value()).isEqualByComparingTo("0");
        assertThat(metric(metrics, MetricCode.WIN_RATE).reasonCode()).isEqualTo("NO_CLOSED_TRADES");
        assertThat(metric(metrics, MetricCode.SHARPE_RATIO).reasonCode()).isEqualTo("INSUFFICIENT_DAILY_RETURNS");
    }

    @Test
    void drawdownUsesRunningPeak() {
        var points = List.of(equity("2026-01-02", "100"), equity("2026-01-05", "120"),
                equity("2026-01-06", "90"));

        assertThat(metric(BacktestMetricsV1.calculate(decimal("100"), points, List.of()),
                MetricCode.MAXIMUM_DRAWDOWN).value()).isEqualByComparingTo("-0.25");
    }

    @Test
    void decimalRootAndRationalPowerMatchIndependentExactVectors() {
        var context = new MathContext(34, RoundingMode.HALF_EVEN);

        assertThat(DeterministicDecimalMath.sqrt(decimal("0.25"), context)).isEqualByComparingTo("0.5");
        assertThat(DeterministicDecimalMath.rationalPower(decimal("1.21"), 1, 2, context))
                .isEqualByComparingTo("1.1");
    }

    @Test
    void twoYearTwentyOnePercentGrowthProducesTenPercentCagr() {
        var points = List.of(equity("2024-01-01", "100"), equity("2025-12-31", "121"));

        assertThat(metric(BacktestMetricsV1.calculate(decimal("100"), points, List.of()), MetricCode.CAGR).value())
                .isEqualByComparingTo("0.10000000");
    }

    @Test
    void sharpeMatchesIndependentSampleDeviationVector() {
        var points = List.of(equity("2026-01-02", "100", null), equity("2026-01-05", "200", "1"),
                equity("2026-01-06", "600", "2"));

        assertThat(metric(BacktestMetricsV1.calculate(decimal("100"), points, List.of()),
                MetricCode.SHARPE_RATIO).value()).isEqualByComparingTo("33.67491648");
    }

    private static BacktestMetricsV1.Metric metric(List<BacktestMetricsV1.Metric> metrics, MetricCode code) {
        return metrics.stream().filter(metric -> metric.code() == code).findFirst().orElseThrow();
    }

    private static BacktestEngineV1.Equity equity(String date, String total) {
        return equity(date, total, null);
    }

    private static BacktestEngineV1.Equity equity(String date, String total, String dailyReturn) {
        return new BacktestEngineV1.Equity(LocalDate.parse(date), decimal(total), BigDecimal.ZERO, decimal(total),
                (short) 0, dailyReturn == null ? null : decimal(dailyReturn), UUID.randomUUID());
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}

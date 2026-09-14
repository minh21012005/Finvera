package com.minhnb.finvera_be.backtest.domain;

import com.minhnb.finvera_be.backtest.domain.BacktestTypes.Availability;
import com.minhnb.finvera_be.backtest.domain.BacktestTypes.MetricCode;
import com.minhnb.finvera_be.backtest.domain.BacktestTypes.MetricUnit;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Các chỉ số backtest xác định; mọi trường hợp không xác định đều có mã lý do. */
public final class BacktestMetricsV1 {
    public static final String RULE_VERSION = "backtest-metrics-v1";
    private static final MathContext MATH_CONTEXT = new MathContext(34, RoundingMode.HALF_EVEN);
    private static final int OUTPUT_SCALE = 8;

    private BacktestMetricsV1() {
    }

    public record Metric(MetricCode code, BigDecimal value, MetricUnit unit, Availability availability,
            String reasonCode) {
    }

    public static List<Metric> calculate(BigDecimal initialCapital, List<BacktestEngineV1.Equity> equityPoints,
            List<BacktestEngineV1.Trade> trades) {
        List<Metric> metrics = new ArrayList<>();
        BigDecimal terminalEquity = equityPoints.isEmpty()
                ? initialCapital
                : equityPoints.get(equityPoints.size() - 1).total();

        metrics.add(defined(MetricCode.TOTAL_RETURN,
                terminalEquity.divide(initialCapital, MATH_CONTEXT).subtract(BigDecimal.ONE, MATH_CONTEXT),
                MetricUnit.RATE));
        addCagr(metrics, initialCapital, terminalEquity, equityPoints);
        addTradeMetrics(metrics, trades);
        addMaximumDrawdown(metrics, initialCapital, equityPoints);
        addSharpeRatio(metrics, equityPoints);
        addAverageTradeReturn(metrics, trades);
        metrics.add(defined(MetricCode.TRADE_COUNT, BigDecimal.valueOf(trades.size()), MetricUnit.COUNT));
        return List.copyOf(metrics);
    }

    private static void addCagr(List<Metric> metrics, BigDecimal initialCapital, BigDecimal terminalEquity,
            List<BacktestEngineV1.Equity> equityPoints) {
        long elapsedDays = equityPoints.size() < 2 ? 0 : ChronoUnit.DAYS.between(
                equityPoints.get(0).date(), equityPoints.get(equityPoints.size() - 1).date());
        if (elapsedDays < 1 || terminalEquity.signum() <= 0) {
            metrics.add(unavailable(MetricCode.CAGR, MetricUnit.RATE, "INSUFFICIENT_ELAPSED_TIME"));
            return;
        }
        BigDecimal ratio = terminalEquity.divide(initialCapital, MATH_CONTEXT);
        BigDecimal cagr = DeterministicDecimalMath
                .rationalPower(ratio, 365, Math.toIntExact(elapsedDays), MATH_CONTEXT)
                .subtract(BigDecimal.ONE, MATH_CONTEXT);
        metrics.add(defined(MetricCode.CAGR, cagr, MetricUnit.RATE));
    }

    private static void addTradeMetrics(List<Metric> metrics, List<BacktestEngineV1.Trade> trades) {
        int tradeCount = trades.size();
        long wins = trades.stream().filter(trade -> trade.netPnl().signum() > 0).count();
        metrics.add(tradeCount == 0
                ? unavailable(MetricCode.WIN_RATE, MetricUnit.RATE, "NO_CLOSED_TRADES")
                : defined(MetricCode.WIN_RATE,
                        BigDecimal.valueOf(wins).divide(BigDecimal.valueOf(tradeCount), MATH_CONTEXT),
                        MetricUnit.RATE));

        BigDecimal gains = trades.stream().map(BacktestEngineV1.Trade::netPnl)
                .filter(value -> value.signum() > 0)
                .reduce(BigDecimal.ZERO, (left, right) -> left.add(right, MATH_CONTEXT));
        BigDecimal losses = trades.stream().map(BacktestEngineV1.Trade::netPnl)
                .filter(value -> value.signum() < 0)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, (left, right) -> left.add(right, MATH_CONTEXT));
        if (tradeCount == 0) {
            metrics.add(unavailable(MetricCode.PROFIT_FACTOR, MetricUnit.RATIO, "NO_CLOSED_TRADES"));
        } else if (losses.signum() == 0) {
            metrics.add(unavailable(MetricCode.PROFIT_FACTOR, MetricUnit.RATIO, "NO_LOSING_TRADES"));
        } else {
            metrics.add(defined(MetricCode.PROFIT_FACTOR, gains.divide(losses, MATH_CONTEXT), MetricUnit.RATIO));
        }
    }

    private static void addMaximumDrawdown(List<Metric> metrics, BigDecimal initialCapital,
            List<BacktestEngineV1.Equity> equityPoints) {
        BigDecimal peak = initialCapital;
        BigDecimal maximumDrawdown = BigDecimal.ZERO;
        for (var point : equityPoints) {
            peak = peak.max(point.total());
            maximumDrawdown = maximumDrawdown.min(
                    point.total().divide(peak, MATH_CONTEXT).subtract(BigDecimal.ONE, MATH_CONTEXT));
        }
        metrics.add(defined(MetricCode.MAXIMUM_DRAWDOWN, maximumDrawdown, MetricUnit.RATE));
    }

    private static void addSharpeRatio(List<Metric> metrics, List<BacktestEngineV1.Equity> equityPoints) {
        List<BigDecimal> returns = equityPoints.stream()
                .map(BacktestEngineV1.Equity::dailyReturn)
                .filter(Objects::nonNull)
                .toList();
        if (returns.size() < 2) {
            metrics.add(unavailable(MetricCode.SHARPE_RATIO, MetricUnit.RATIO,
                    "INSUFFICIENT_DAILY_RETURNS"));
            return;
        }

        BigDecimal mean = returns.stream()
                .reduce(BigDecimal.ZERO, (left, right) -> left.add(right, MATH_CONTEXT))
                .divide(BigDecimal.valueOf(returns.size()), MATH_CONTEXT);
        BigDecimal variance = returns.stream()
                .map(value -> value.subtract(mean, MATH_CONTEXT).pow(2, MATH_CONTEXT))
                .reduce(BigDecimal.ZERO, (left, right) -> left.add(right, MATH_CONTEXT))
                .divide(BigDecimal.valueOf(returns.size() - 1L), MATH_CONTEXT);
        if (variance.signum() == 0) {
            metrics.add(unavailable(MetricCode.SHARPE_RATIO, MetricUnit.RATIO, "ZERO_RETURN_VARIANCE"));
            return;
        }

        BigDecimal standardDeviation = DeterministicDecimalMath.sqrt(variance, MATH_CONTEXT);
        BigDecimal annualizer = DeterministicDecimalMath.sqrt(BigDecimal.valueOf(252), MATH_CONTEXT);
        metrics.add(defined(MetricCode.SHARPE_RATIO,
                mean.divide(standardDeviation, MATH_CONTEXT).multiply(annualizer, MATH_CONTEXT),
                MetricUnit.RATIO));
    }

    private static void addAverageTradeReturn(List<Metric> metrics, List<BacktestEngineV1.Trade> trades) {
        if (trades.isEmpty()) {
            metrics.add(unavailable(MetricCode.AVERAGE_TRADE_RETURN, MetricUnit.RATE, "NO_CLOSED_TRADES"));
            return;
        }
        BigDecimal average = trades.stream().map(BacktestEngineV1.Trade::tradeReturn)
                .reduce(BigDecimal.ZERO, (left, right) -> left.add(right, MATH_CONTEXT))
                .divide(BigDecimal.valueOf(trades.size()), MATH_CONTEXT);
        metrics.add(defined(MetricCode.AVERAGE_TRADE_RETURN, average, MetricUnit.RATE));
    }

    private static Metric defined(MetricCode code, BigDecimal value, MetricUnit unit) {
        return new Metric(code, value.setScale(OUTPUT_SCALE, RoundingMode.HALF_EVEN), unit, Availability.DEFINED, null);
    }

    private static Metric unavailable(MetricCode code, MetricUnit unit, String reasonCode) {
        return new Metric(code, null, unit, Availability.UNAVAILABLE, reasonCode);
    }
}

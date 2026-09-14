package com.minhnb.finvera_be.backtest.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Stable vocabulary shared by the versioned backtest engines and adapters. */
public final class BacktestTypes {
    private BacktestTypes() { }

    public enum RunStatus { QUEUED, RUNNING, COMPLETED, WITHHELD, FAILED }
    public enum ExitReason { STOP_LOSS, TARGET1, END_OF_PERIOD }
    public enum EntryOutcome { REJECTED, CANCELLED }
    public enum MetricCode {
        TOTAL_RETURN, CAGR, WIN_RATE, PROFIT_FACTOR, MAXIMUM_DRAWDOWN,
        SHARPE_RATIO, AVERAGE_TRADE_RETURN, TRADE_COUNT
    }
    public enum MetricUnit { RATE, RATIO, COUNT }
    public enum Availability { DEFINED, UNAVAILABLE }
    public enum EvidenceUnit { TEXT, DATE, INSTANT, VND, RATE, COUNT }

    public record SessionBar(
            UUID barId, LocalDate tradingDate, BigDecimal rawOpen,
            BigDecimal rawHigh, BigDecimal rawLow, BigDecimal rawClose,
            BigDecimal adjustmentFactor, String adjustmentStatus,
            String source, Instant observedAt, Instant acceptedAt) {
        public SessionBar {
            Objects.requireNonNull(barId);
            Objects.requireNonNull(tradingDate);
            requirePositive(rawHigh, "rawHigh");
            requirePositive(rawLow, "rawLow");
            requirePositive(rawClose, "rawClose");
            requirePositive(adjustmentFactor, "adjustmentFactor");
            Objects.requireNonNull(adjustmentStatus);
            Objects.requireNonNull(source);
            Objects.requireNonNull(observedAt);
            Objects.requireNonNull(acceptedAt);
            if (rawOpen != null && rawOpen.signum() <= 0) throw new IllegalArgumentException("INVALID_RAWOPEN");
            if (rawHigh.compareTo(rawLow) < 0 || (rawOpen != null && rawHigh.compareTo(rawOpen) < 0)
                    || rawHigh.compareTo(rawClose) < 0 || (rawOpen != null && rawLow.compareTo(rawOpen) > 0)
                    || rawLow.compareTo(rawClose) > 0) {
                throw new IllegalArgumentException("INVALID_OHLC");
            }
        }
    }

    public record Costs(BigDecimal entryFeeRate, BigDecimal exitFeeRate,
            BigDecimal sellTaxRate, BigDecimal entrySlippageRate,
            BigDecimal exitSlippageRate, boolean excluded) {
        public Costs {
            entryFeeRate = rate(entryFeeRate, "entryFeeRate");
            exitFeeRate = rate(exitFeeRate, "exitFeeRate");
            sellTaxRate = rate(sellTaxRate, "sellTaxRate");
            entrySlippageRate = rate(entrySlippageRate, "entrySlippageRate");
            exitSlippageRate = rate(exitSlippageRate, "exitSlippageRate");
            boolean allZero = entryFeeRate.signum() == 0 && exitFeeRate.signum() == 0
                    && sellTaxRate.signum() == 0 && entrySlippageRate.signum() == 0
                    && exitSlippageRate.signum() == 0;
            if (excluded != allZero) throw new IllegalArgumentException("INVALID_COST_POLICY");
            if (exitFeeRate.add(sellTaxRate).compareTo(BigDecimal.ONE) >= 0) {
                throw new IllegalArgumentException("INVALID_EXIT_COST_RATE");
            }
        }
    }

    public record Assumptions(BigDecimal initialCapitalVnd,
            BigDecimal riskPerTrancheRate, BigDecimal maxAggregateOpenRiskRate,
            Costs costs) {
        public Assumptions {
            requirePositive(initialCapitalVnd, "initialCapitalVnd");
            riskPerTrancheRate = positiveRate(riskPerTrancheRate, "riskPerTrancheRate");
            maxAggregateOpenRiskRate = positiveRate(maxAggregateOpenRiskRate, "maxAggregateOpenRiskRate");
            if (maxAggregateOpenRiskRate.compareTo(riskPerTrancheRate) < 0) {
                throw new IllegalArgumentException("AGGREGATE_RISK_BELOW_TRANCHE_RISK");
            }
            Objects.requireNonNull(costs);
        }
    }

    private static void requirePositive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0 || value.precision() > 20
                || Math.max(value.scale(), 0) > 6) {
            throw new IllegalArgumentException("INVALID_" + field.toUpperCase());
        }
    }

    private static BigDecimal rate(BigDecimal value, String field) {
        if (value == null || value.signum() < 0 || value.compareTo(BigDecimal.ONE) >= 0
                || value.precision() > 20 || Math.max(value.scale(), 0) > 8) {
            throw new IllegalArgumentException("INVALID_" + field.toUpperCase());
        }
        return value;
    }

    private static BigDecimal positiveRate(BigDecimal value, String field) {
        BigDecimal result = rate(value, field);
        if (result.signum() == 0) throw new IllegalArgumentException("INVALID_" + field.toUpperCase());
        return result;
    }
}

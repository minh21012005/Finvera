package com.minhnb.finvera_be.stock.domain.model;

/**
 * Feature 002 introduces its own {@code AdjustmentStatus} because its value
 * set ({@code ADJUSTED}) differs from
 * {@code market.domain.model.MarketTypes.AdjustmentStatus} ({@code
 * PROVIDER_ADJUSTED}); every other reused vocabulary (DataStatus,
 * SessionState, Direction) stays in {@code MarketTypes} and is imported
 * directly rather than duplicated here.
 */
public final class StockTypes {

    private StockTypes() {
    }

    public enum AdjustmentStatus {
        ADJUSTED, RAW, NOT_APPLICABLE, UNKNOWN
    }

    public enum MetricApplicability {
        DEFINED, NOT_APPLICABLE, MISSING
    }

    public enum ValuationLabel {
        UNDER_VALUED, FAIR_VALUED, OVER_VALUED
    }

    public enum ComparisonBasis {
        OWN_HISTORY, SECTOR
    }

    public enum PeriodType {
        ANNUAL, QUARTER
    }

    public enum ReportKind {
        CONSOLIDATED, SEPARATE, UNKNOWN
    }

    public enum AuditStatus {
        AUDITED, REVIEWED, UNAUDITED, UNKNOWN
    }

    public enum CorporateActionType {
        SPLIT, STOCK_DIVIDEND, CASH_DIVIDEND, RIGHTS_ISSUE, OTHER
    }

    public enum IndicatorCode {
        MA20, MA50, MA200, RSI14, MACD, BBANDS, ATR14, AVG_VOLUME20, RELATIVE_VOLUME
    }

    public enum IndicatorComponent {
        VALUE, UPPER, MIDDLE, LOWER, BANDWIDTH, MACD_LINE, SIGNAL, HISTOGRAM, PERCENT_OF_CLOSE
    }

    public enum ValuationMetricCode {
        PE, PB, EV_EBITDA, PEG, DIVIDEND_YIELD
    }

    public enum Unit {
        VND, SHARES, PERCENT, RATIO, POINTS, COUNT
    }

    /** Feature 004 {@code strategy-signal-v1}: fixed, code-defined (data-model.md). */
    public enum StrategyCode {
        TREND_FOLLOWING, MOMENTUM, BREAKOUT, PULLBACK, MEAN_REVERSION, MA_CROSSOVER, MACD_BASED, RSI_BASED
    }

    /** Single value in this feature (spec.md Assumptions); an enum so a future value is additive. */
    public enum Direction {
        LONG
    }

    public enum RiskLevel {
        LOW, MEDIUM, HIGH
    }

    /** Derived from {@link RiskLevel} at read time (research R-005); never independently stored. */
    public enum SignalStrength {
        WEAK, MODERATE, STRONG
    }

    public enum RiskFactorCode {
        VOLATILITY, ATR, DRAWDOWN, LIQUIDITY, STOP_DISTANCE, MARKET_REGIME
    }
}

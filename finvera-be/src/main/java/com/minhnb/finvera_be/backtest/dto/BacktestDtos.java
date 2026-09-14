package com.minhnb.finvera_be.backtest.dto;

import com.minhnb.finvera_be.backtest.domain.BacktestTypes.Availability;
import com.minhnb.finvera_be.backtest.domain.BacktestTypes.EntryOutcome;
import com.minhnb.finvera_be.backtest.domain.BacktestTypes.EvidenceUnit;
import com.minhnb.finvera_be.backtest.domain.BacktestTypes.ExitReason;
import com.minhnb.finvera_be.backtest.domain.BacktestTypes.MetricCode;
import com.minhnb.finvera_be.backtest.domain.BacktestTypes.MetricUnit;
import com.minhnb.finvera_be.backtest.domain.BacktestTypes.RunStatus;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.StrategyCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class BacktestDtos {
    private BacktestDtos() { }
    public static final String MONEY="^(?=.{1,21}$)(?=\\d+(?:\\.\\d+)?$)(?!(?:\\d{21}|\\d{15,}\\.\\d{6}$))\\d{1,20}(?:\\.\\d{1,6})?$";
    public static final String POSITIVE_RATE="^(?:0\\.(?=\\d{0,7}[1-9])\\d{1,8}|1(?:\\.0{1,8})?)$";
    public static final String COST_RATE="^(?:0(?:\\.\\d{1,8})?)$";

    public record CreateRequest(@NotNull StrategyCode strategyCode,
            @NotBlank @Pattern(regexp="^[A-Z0-9]{1,10}$") String symbol,
            @NotNull LocalDate startDate,@NotNull LocalDate endDate,
            @NotBlank @Pattern(regexp=MONEY) String initialCapitalVnd,
            @NotBlank @Pattern(regexp=POSITIVE_RATE) String riskPerTrancheRate,
            @NotBlank @Pattern(regexp=POSITIVE_RATE) String maxAggregateOpenRiskRate,
            @NotNull @Valid CostPolicy costs) { }

    public record CostPolicy(@NotNull Boolean excluded,
            @Pattern(regexp=COST_RATE) String entryFeeRate,
            @Pattern(regexp=COST_RATE) String exitFeeRate,
            @Pattern(regexp=COST_RATE) String sellTaxRate,
            @Pattern(regexp=COST_RATE) String entrySlippageRate,
            @Pattern(regexp=COST_RATE) String exitSlippageRate) { }

    public record RunSummary(UUID id,RunStatus status,StrategyCode strategyCode,String symbol,
            LocalDate startDate,LocalDate endDate,int processedSessions,Integer totalSessions,
            String reasonCode,Instant createdAt,Instant completedAt) { }
    public record Page<T>(List<T> items,long total,int limit,int offset) { }
    public record Metric(MetricCode code,String value,MetricUnit unit,Availability availability,
            String reasonCode,String ruleVersion) { }
    public record Evidence(String key,String value,EvidenceUnit unit) { }
    public record Assumptions(String initialCapitalVnd,String riskPerTrancheRate,
            String maxAggregateOpenRiskRate,CostPolicy costs,String strategyRuleVersion,
            String sizingRuleVersion,String engineRuleVersion,String metricsRuleVersion,
            String pyramidingRuleVersion,String entryTiming,String sameBarPriority,
            int maxOpenTranches,String pyramidStepAtr) { }
    public record RunDetail(UUID id,RunStatus status,StrategyCode strategyCode,String symbol,
            LocalDate startDate,LocalDate endDate,int processedSessions,Integer totalSessions,
            String reasonCode,Instant createdAt,Instant completedAt,Instant dataCutoffAcceptedAt,
            Assumptions assumptions,List<Metric> metrics,List<Evidence> evidence,List<String> warnings) { }
    public record Trade(int sequence,LocalDate signalDate,LocalDate entryDate,LocalDate exitDate,
            long quantity,String rawEntryPriceVnd,String effectiveEntryPriceVnd,
            String effectiveExitPriceVnd,String acquisitionCostVnd,String netExitProceedsVnd,
            String netPnlVnd,String tradeReturnRate,ExitReason exitReason) { }
    public record EquityPoint(LocalDate tradingDate,String cashVnd,String openPositionValueVnd,
            String totalEquityVnd,short openTrancheCount,String dailyReturnRate) { }
    public record EntryEvent(int sequence,LocalDate signalDate,LocalDate executionDate,
            EntryOutcome outcome,String reasonCode,short openTrancheCount) { }
}

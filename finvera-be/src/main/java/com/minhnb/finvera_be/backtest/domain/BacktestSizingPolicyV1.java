package com.minhnb.finvera_be.backtest.domain;

import com.minhnb.finvera_be.backtest.domain.BacktestTypes.Assumptions;
import com.minhnb.finvera_be.positioning.domain.PositionSizingV1;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

/** Adapter thuần dùng đúng position-sizing-v1 và áp trần tổng rủi ro đang mở. */
public final class BacktestSizingPolicyV1 {
    private static final MathContext MC = new MathContext(34, RoundingMode.HALF_EVEN);
    private BacktestSizingPolicyV1() { }

    public record OpenRisk(long quantity, BigDecimal acquisitionUnitCost, BigDecimal netStopProceedsPerShare) { }
    public record Decision(PositionSizingV1.Result sizing, BigDecimal remainingRiskVnd, String reasonCode) {
        public boolean accepted() { return sizing != null && sizing.calculated(); }
    }

    public static Decision size(BigDecimal equity, BigDecimal cash, BigDecimal rawEntry, BigDecimal stop,
            Assumptions assumptions, long lotSize, List<OpenRisk> openRisks) {
        BigDecimal cap = equity.multiply(assumptions.maxAggregateOpenRiskRate(), MC);
        BigDecimal used = (openRisks == null ? List.<OpenRisk>of() : openRisks).stream()
                .map(x -> BigDecimal.valueOf(x.quantity()).multiply(
                        x.acquisitionUnitCost().subtract(x.netStopProceedsPerShare(), MC).max(BigDecimal.ZERO), MC))
                .reduce(BigDecimal.ZERO, (a,b) -> a.add(b, MC));
        BigDecimal remaining = cap.subtract(used, MC).max(BigDecimal.ZERO);
        BigDecimal tranche = equity.multiply(assumptions.riskPerTrancheRate(), MC);
        BigDecimal budget = tranche.min(remaining);
        if (budget.signum() <= 0) return new Decision(null, remaining, "AGGREGATE_RISK_EXHAUSTED");
        var c = assumptions.costs();
        var costs = new PositionSizingV1.Costs(c.entryFeeRate(), c.exitFeeRate(), c.sellTaxRate(),
                c.entrySlippageRate(), c.exitSlippageRate());
        var result = PositionSizingV1.calculate(new PositionSizingV1.Input(equity, cash, rawEntry, stop, budget,
                costs, null, null, null, null, null, lotSize));
        return new Decision(result, remaining, result.calculated() ? null : "BELOW_STANDARD_LOT");
    }
}

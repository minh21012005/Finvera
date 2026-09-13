package com.minhnb.finvera_be.positioning.domain;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Pure, stateless and versioned long-equity position-sizing formula. */
public final class PositionSizingV1 {
    public static final String RULE_VERSION = "position-sizing-v1";
    public static final String LOT_RULE_VERSION = "market-lot-v1";
    private static final MathContext MC = new MathContext(34, RoundingMode.HALF_EVEN);
    private static final BigDecimal ONE = BigDecimal.ONE;

    private PositionSizingV1() { }

    public enum ConstraintCode { RISK_BUDGET, AFFORDABILITY, SYMBOL_CONCENTRATION, TOTAL_DEPLOYMENT }
    public enum Applicability { APPLIED, NOT_APPLIED }

    public record Costs(
            BigDecimal entryFeeRate,
            BigDecimal exitFeeRate,
            BigDecimal sellTaxRate,
            BigDecimal entrySlippageRate,
            BigDecimal exitSlippageRate) {
        public Costs {
            entryFeeRate = rate(entryFeeRate, "entryFeeRate");
            exitFeeRate = rate(exitFeeRate, "exitFeeRate");
            sellTaxRate = rate(sellTaxRate, "sellTaxRate");
            entrySlippageRate = rate(entrySlippageRate, "entrySlippageRate");
            exitSlippageRate = rate(exitSlippageRate, "exitSlippageRate");
            if (exitFeeRate.add(sellTaxRate, MC).compareTo(ONE) >= 0) {
                throw new ValidationException("INVALID_EXIT_COST_RATE");
            }
        }

        public static Costs excluded() {
            return new Costs(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    public record Input(
            BigDecimal capitalBaseVnd,
            BigDecimal availableCashVnd,
            BigDecimal entryPriceVnd,
            BigDecimal stopPriceVnd,
            BigDecimal riskBudgetVnd,
            Costs costs,
            BigDecimal portfolioValueVnd,
            BigDecimal existingSymbolMarketValueVnd,
            BigDecimal currentDeployedMarketValueVnd,
            BigDecimal maxSymbolConcentrationRate,
            BigDecimal maxDeploymentRate,
            long lotSize) {
        public Input {
            capitalBaseVnd = positiveMoney(capitalBaseVnd, "capitalBaseVnd");
            availableCashVnd = nonNegativeMoney(availableCashVnd, "availableCashVnd");
            entryPriceVnd = positiveMoney(entryPriceVnd, "entryPriceVnd");
            stopPriceVnd = positiveMoney(stopPriceVnd, "stopPriceVnd");
            riskBudgetVnd = positiveMoney(riskBudgetVnd, "riskBudgetVnd");
            costs = Objects.requireNonNull(costs, "costs");
            if (stopPriceVnd.compareTo(entryPriceVnd) >= 0) throw new ValidationException("STOP_NOT_BELOW_ENTRY");
            if (lotSize <= 0) throw new ValidationException("INVALID_LOT_SIZE");
            if (maxSymbolConcentrationRate != null) {
                maxSymbolConcentrationRate = positiveRate(maxSymbolConcentrationRate, "maxSymbolConcentrationRate");
                portfolioValueVnd = positiveMoney(portfolioValueVnd, "portfolioValueVnd");
                existingSymbolMarketValueVnd = nonNegativeMoney(existingSymbolMarketValueVnd, "existingSymbolMarketValueVnd");
            }
            if (maxDeploymentRate != null) {
                maxDeploymentRate = positiveRate(maxDeploymentRate, "maxDeploymentRate");
                portfolioValueVnd = positiveMoney(portfolioValueVnd, "portfolioValueVnd");
                currentDeployedMarketValueVnd = nonNegativeMoney(currentDeployedMarketValueVnd, "currentDeployedMarketValueVnd");
            }
        }
    }

    public record Constraint(ConstraintCode code, Applicability applicability, Long rawQuantity, boolean binding) { }

    public record Result(
            boolean calculated,
            Long quantity,
            Long rawPermittedQuantity,
            long lotSize,
            Long roundingRemainder,
            BigDecimal effectiveEntryPriceVnd,
            BigDecimal effectiveStopPriceVnd,
            BigDecimal riskBudgetVnd,
            BigDecimal acquisitionUnitCostVnd,
            BigDecimal stopNetProceedsPerShareVnd,
            BigDecimal lossPerShareVnd,
            BigDecimal requiredCapitalVnd,
            BigDecimal estimatedLossAtStopVnd,
            BigDecimal remainingCashVnd,
            BigDecimal projectedSymbolMarketValueVnd,
            BigDecimal projectedSymbolExposureRate,
            BigDecimal projectedDeploymentRate,
            List<Constraint> constraints,
            List<String> reasonCodes) { }

    public static Result calculate(Input in) {
        Objects.requireNonNull(in, "input");
        Costs c = in.costs();
        BigDecimal effectiveEntry = in.entryPriceVnd().multiply(ONE.add(c.entrySlippageRate(), MC), MC);
        BigDecimal effectiveStop = in.stopPriceVnd().multiply(ONE.subtract(c.exitSlippageRate(), MC), MC);
        BigDecimal acquisitionUnitCost = effectiveEntry.multiply(ONE.add(c.entryFeeRate(), MC), MC);
        BigDecimal stopNet = effectiveStop.multiply(
                ONE.subtract(c.exitFeeRate(), MC).subtract(c.sellTaxRate(), MC), MC);
        BigDecimal lossPerShare = acquisitionUnitCost.subtract(stopNet, MC);
        if (lossPerShare.signum() <= 0) throw new ValidationException("NON_POSITIVE_LOSS_PER_SHARE");

        List<Candidate> candidates = new ArrayList<>();
        candidates.add(new Candidate(ConstraintCode.RISK_BUDGET, floorShares(in.riskBudgetVnd(), lossPerShare)));
        candidates.add(new Candidate(ConstraintCode.AFFORDABILITY, floorShares(in.availableCashVnd(), acquisitionUnitCost)));
        if (in.maxSymbolConcentrationRate() != null) {
            BigDecimal headroom = in.portfolioValueVnd().multiply(in.maxSymbolConcentrationRate(), MC)
                    .subtract(in.existingSymbolMarketValueVnd(), MC).max(BigDecimal.ZERO);
            candidates.add(new Candidate(ConstraintCode.SYMBOL_CONCENTRATION, floorShares(headroom, effectiveEntry)));
        }
        if (in.maxDeploymentRate() != null) {
            BigDecimal headroom = in.portfolioValueVnd().multiply(in.maxDeploymentRate(), MC)
                    .subtract(in.currentDeployedMarketValueVnd(), MC).max(BigDecimal.ZERO);
            candidates.add(new Candidate(ConstraintCode.TOTAL_DEPLOYMENT, floorShares(headroom, effectiveEntry)));
        }
        long raw = candidates.stream().map(Candidate::quantity).min(Comparator.naturalOrder()).orElseThrow();
        long quantity = (raw / in.lotSize()) * in.lotSize();

        List<Constraint> constraints = new ArrayList<>();
        for (ConstraintCode code : ConstraintCode.values()) {
            Candidate applied = candidates.stream().filter(x -> x.code() == code).findFirst().orElse(null);
            constraints.add(applied == null
                    ? new Constraint(code, Applicability.NOT_APPLIED, null, false)
                    : new Constraint(code, Applicability.APPLIED, applied.quantity(), applied.quantity() == raw));
        }

        BigDecimal qty = BigDecimal.valueOf(quantity);
        BigDecimal requiredCapital = acquisitionUnitCost.multiply(qty, MC);
        BigDecimal estimatedLoss = lossPerShare.multiply(qty, MC);
        BigDecimal proposedGross = effectiveEntry.multiply(qty, MC);
        BigDecimal projectedSymbolValue = in.existingSymbolMarketValueVnd() == null
                ? null : in.existingSymbolMarketValueVnd().add(proposedGross, MC);
        BigDecimal projectedSymbolRate = projectedSymbolValue == null || in.portfolioValueVnd() == null
                ? null : projectedSymbolValue.divide(in.portfolioValueVnd(), MC);
        BigDecimal projectedDeploymentRate = in.currentDeployedMarketValueVnd() == null || in.portfolioValueVnd() == null
                ? null : in.currentDeployedMarketValueVnd().add(proposedGross, MC).divide(in.portfolioValueVnd(), MC);
        boolean calculated = quantity >= in.lotSize();
        return new Result(calculated, calculated ? quantity : null, raw, in.lotSize(), raw - quantity,
                effectiveEntry, effectiveStop, in.riskBudgetVnd(), acquisitionUnitCost, stopNet, lossPerShare,
                requiredCapital, estimatedLoss, in.availableCashVnd().subtract(requiredCapital, MC),
                projectedSymbolValue, projectedSymbolRate, projectedDeploymentRate, List.copyOf(constraints),
                calculated ? List.of() : List.of("BELOW_STANDARD_LOT"));
    }

    private record Candidate(ConstraintCode code, long quantity) { }

    private static long floorShares(BigDecimal numerator, BigDecimal denominator) {
        BigInteger value = numerator.divideToIntegralValue(denominator, MC).toBigIntegerExact();
        if (value.signum() < 0 || value.bitLength() > 63) throw new ValidationException("QUANTITY_OUT_OF_RANGE");
        return value.longValueExact();
    }

    private static BigDecimal positiveMoney(BigDecimal value, String field) {
        BigDecimal result = decimal(value, field, 6);
        if (result.signum() <= 0) throw new ValidationException("INVALID_" + field.toUpperCase());
        return result;
    }

    private static BigDecimal nonNegativeMoney(BigDecimal value, String field) {
        BigDecimal result = decimal(value, field, 6);
        if (result.signum() < 0) throw new ValidationException("INVALID_" + field.toUpperCase());
        return result;
    }

    private static BigDecimal rate(BigDecimal value, String field) {
        BigDecimal result = decimal(value, field, 8);
        if (result.signum() < 0 || result.compareTo(ONE) >= 0) throw new ValidationException("INVALID_" + field.toUpperCase());
        return result;
    }

    private static BigDecimal positiveRate(BigDecimal value, String field) {
        BigDecimal result = decimal(value, field, 8);
        if (result.signum() <= 0 || result.compareTo(ONE) > 0) throw new ValidationException("INVALID_" + field.toUpperCase());
        return result;
    }

    private static BigDecimal decimal(BigDecimal value, String field, int maxScale) {
        if (value == null || value.precision() > 20 || Math.max(0, value.scale()) > maxScale) {
            throw new ValidationException("INVALID_" + field.toUpperCase());
        }
        return value;
    }

    public static final class ValidationException extends IllegalArgumentException {
        private final String reasonCode;
        public ValidationException(String reasonCode) { super(reasonCode); this.reasonCode = reasonCode; }
        public String reasonCode() { return reasonCode; }
    }
}

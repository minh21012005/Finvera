package com.minhnb.finvera_be.positioning.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class PositionSizingDtos {
    private PositionSizingDtos() { }

    public record SizingRequest(
            @NotNull Mode mode,
            @NotBlank @Pattern(regexp = "^[A-Z0-9]{3,10}$") String symbol,
            UUID portfolioId,
            @Valid ManualCapital manualCapital,
            @NotNull @Valid RiskBudget riskBudget,
            @NotNull @Valid PriceInput priceInput,
            @NotNull @Valid CostPolicy costPolicy,
            @Valid ExposureLimits exposureLimits) {
        @Override public String toString() {
            return "SizingRequest[mode=" + mode + ", symbol=<redacted>, financialInputs=<redacted>]";
        }
        @JsonAnySetter public void rejectUnknown(String name, Object value) { throw new IllegalArgumentException("Unknown request field"); }
    }

    public enum Mode { MANUAL, PORTFOLIO }
    public enum RiskKind { FIXED_VND, PERCENT }
    public enum PriceSource { MANUAL, SIGNAL }
    public enum EntryBasis { ENTRY_LOW, MIDPOINT, ENTRY_HIGH }

    public record ManualCapital(@NotBlank String capitalBaseVnd, @NotBlank String availableCashVnd,
            String portfolioValueVnd, String existingSymbolMarketValueVnd,
            String currentDeployedMarketValueVnd) {
        @JsonAnySetter public void rejectUnknown(String name, Object value) { throw new IllegalArgumentException("Unknown capital field"); }
    }
    public record RiskBudget(@NotNull RiskKind kind, @NotBlank String value) {
        @JsonAnySetter public void rejectUnknown(String name, Object value) { throw new IllegalArgumentException("Unknown risk field"); }
    }
    public record PriceInput(@NotNull PriceSource source, String entryPriceVnd, String stopPriceVnd,
            String strategyCode, String ruleVersion, Instant calculatedAt, EntryBasis entryBasis,
            Boolean confirmed, @Valid OriginatingSignalContext originatingSignalContext) {
        public PriceInput(PriceSource source, String entryPriceVnd, String stopPriceVnd,
                String strategyCode, String ruleVersion, Instant calculatedAt, EntryBasis entryBasis,
                Boolean confirmed) {
            this(source, entryPriceVnd, stopPriceVnd, strategyCode, ruleVersion, calculatedAt, entryBasis,
                    confirmed, null);
        }
        @JsonAnySetter public void rejectUnknown(String name, Object value) { throw new IllegalArgumentException("Unknown price field"); }
    }
    public record OriginatingSignalContext(@NotBlank String strategyCode, @NotBlank String ruleVersion,
            @NotNull Instant calculatedAt, @NotNull LocalDate asOfTradingDate) {
        @JsonAnySetter public void rejectUnknown(String name, Object value) { throw new IllegalArgumentException("Unknown signal context field"); }
    }
    public record CostPolicy(@NotNull Boolean excludeCosts, String entryFeeRate, String exitFeeRate,
            String sellTaxRate, String entrySlippageRate, String exitSlippageRate) {
        @JsonAnySetter public void rejectUnknown(String name, Object value) { throw new IllegalArgumentException("Unknown cost field"); }
    }
    public record ExposureLimits(String maxSymbolConcentrationRate, String maxDeploymentRate) {
        @JsonAnySetter public void rejectUnknown(String name, Object value) { throw new IllegalArgumentException("Unknown exposure field"); }
    }

    public record SizingResult(String status, String symbol, String mode, Long quantity,
            Long rawPermittedQuantity, long lotSize, Long roundingRemainder,
            String capitalBaseVnd, String availableCashVnd, String resolvedEntryPriceVnd,
            String resolvedStopPriceVnd, String effectiveEntryPriceVnd, String effectiveStopPriceVnd,
            boolean costsExcluded, String riskBudgetVnd, String acquisitionUnitCostVnd,
            String stopNetProceedsPerShareVnd, String lossPerShareVnd, String requiredCapitalVnd,
            String estimatedLossAtStopVnd, String remainingCashVnd, String projectedSymbolMarketValueVnd,
            String projectedSymbolExposureRate, String projectedDeploymentRate,
            List<ConstraintResult> constraints, List<InputEvidence> inputEvidence,
            List<String> reasonCodes, List<String> warnings, String sizingRuleVersion,
            String marketRuleVersion, Instant calculatedAt) {
        @Override public String toString() {
            return "SizingResult[status=" + status + ", symbol=<redacted>, financialOutputs=<redacted>, sizingRuleVersion="
                    + sizingRuleVersion + "]";
        }
    }

    public record ConstraintResult(String code, String applicability, Long rawQuantity, boolean binding) { }
    public record InputEvidence(String field, String value, String source, String unit,
            Instant asOf, String coherenceKey) { }
}

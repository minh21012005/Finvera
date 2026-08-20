package com.minhnb.finvera_be.portfolio.domain.analytics;

import com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioValidationException.ReasonCode;
import com.minhnb.finvera_be.portfolio.domain.model.PortfolioTypes.TransactionType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Pure, framework-free calculation engine implementing calculation contract
 * {@code portfolio-analytics-v1}.
 *
 * <p>Rule version: {@code portfolio-analytics-v1}.
 */
public final class PortfolioAnalyticsV1 {

    public static final String RULE_VERSION = "portfolio-analytics-v1";
    public static final String DISCLOSURE_CODE = "NET_CONTRIBUTED_CAPITAL_METHOD";
    public static final int MONEY_SCALE = 6;
    public static final int RATIO_SCALE = 4;
    public static final int INTERNAL_SCALE = 12;

    /** Market-facing dates use Vietnam time, never host/UTC (AGENTS.md, Constitution). */
    private static final java.time.ZoneId MARKET_ZONE = java.time.ZoneId.of("Asia/Ho_Chi_Minh");

    private static final Comparator<TransactionInput> CHRONOLOGICAL_ORDER =
            Comparator.comparing(TransactionInput::executedAt)
                    .thenComparingLong(TransactionInput::sequenceNo);

    private PortfolioAnalyticsV1() {
    }

    // --- Inputs & Result Records ---

    public record TransactionInput(
            UUID id,
            long sequenceNo,
            TransactionType type,
            UUID instrumentId,
            String symbol,
            BigDecimal quantity,
            BigDecimal price,
            BigDecimal fee,
            BigDecimal amount,
            String currency,
            Instant executedAt,
            Instant entryAt,
            UUID voidsTransactionId,
            String voidReason) {

        public TransactionInput {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(executedAt, "executedAt");
            fee = fee != null ? fee : BigDecimal.ZERO;
            currency = currency != null ? currency : "VND";
        }
    }

    public record FifoLot(
            UUID transactionId,
            UUID instrumentId,
            BigDecimal quantityRemaining,
            BigDecimal initialQuantity,
            BigDecimal unitCost,
            Instant executedAt,
            long sequenceNo) {
    }

    public record PositionResult(
            UUID instrumentId,
            String symbol,
            BigDecimal quantity,
            BigDecimal averageCostBasis,
            BigDecimal currentPrice,
            BigDecimal marketValue,
            BigDecimal unrealizedPL,
            BigDecimal unrealizedPLPercent,
            BigDecimal realizedPL,
            BigDecimal allocation,
            boolean priceAvailable) {
    }

    public record PortfolioTotalsResult(
            BigDecimal totalValue,
            BigDecimal totalPositionsValue,
            BigDecimal cashBalance,
            BigDecimal totalUnrealizedPL,
            BigDecimal totalRealizedPL,
            BigDecimal netContributedCapital) {
    }

    public record PortfolioHoldingsState(
            Map<UUID, PositionResult> positions,
            PortfolioTotalsResult totals,
            List<FifoLot> openLots,
            BigDecimal cashBalance,
            BigDecimal netContributedCapital) {
    }

    public record ReturnResult(
            BigDecimal returnSinceInception,
            BigDecimal returnOverPeriod,
            String disclosureCode,
            boolean available) {
    }

    public record DrawdownPoint(
            LocalDate date,
            BigDecimal totalValue,
            BigDecimal peakValue,
            BigDecimal drawdown,
            boolean partial,
            String partialReasonSymbol) {
    }

    public record PerformanceHistoryResult(
            LocalDate periodFrom,
            LocalDate periodTo,
            boolean periodClampedToInception,
            BigDecimal maxDrawdown,
            List<DrawdownPoint> series) {
    }

    public record StockConcentrationItem(
            UUID instrumentId,
            String symbol,
            BigDecimal marketValue,
            BigDecimal weight) {
    }

    public record SectorConcentrationItem(
            String sectorDisplayNameVi,
            String sectorDisplayNameEn,
            BigDecimal marketValue,
            BigDecimal weight) {
    }

    public record SignalRiskInput(
            UUID instrumentId,
            Integer riskScore,
            String riskLevel) {
    }

    public record RiskExposureResult(
            Integer riskExposureScore,
            String riskExposureLevel,
            BigDecimal coverageRatio,
            BigDecimal coveredPositionsValue,
            BigDecimal totalPositionsValue) {
    }

    public record BenchmarkComparisonResult(
            LocalDate periodFrom,
            LocalDate periodTo,
            BigDecimal portfolioReturn,
            BigDecimal benchmarkReturn,
            BigDecimal alpha) {
    }

    // --- Validation Methods ---

    /**
     * Validates a proposed transaction against the current ledger history.
     * Throws {@link PortfolioValidationException} if validation fails.
     */
    public static void validateTransaction(
            Collection<TransactionInput> existingTransactions,
            TransactionInput newTransaction) {
        Objects.requireNonNull(existingTransactions, "existingTransactions");
        Objects.requireNonNull(newTransaction, "newTransaction");

        // Validate type-specific constraints
        switch (newTransaction.type()) {
            case VOID -> validateVoid(existingTransactions, newTransaction);
            case SELL -> validateSell(existingTransactions, newTransaction);
            case WITHDRAW -> validateWithdraw(existingTransactions, newTransaction);
            case BUY -> {
                if (newTransaction.quantity() == null || newTransaction.quantity().signum() <= 0) {
                    throw new PortfolioValidationException(ReasonCode.INVALID_TRANSACTION_TYPE, "BUY quantity must be positive");
                }
                if (newTransaction.price() == null || newTransaction.price().signum() <= 0) {
                    throw new PortfolioValidationException(ReasonCode.INVALID_TRANSACTION_TYPE, "BUY price must be positive");
                }
            }
            case DEPOSIT -> {
                if (newTransaction.amount() == null || newTransaction.amount().signum() <= 0) {
                    throw new PortfolioValidationException(ReasonCode.INVALID_TRANSACTION_TYPE, "DEPOSIT amount must be positive");
                }
            }
        }
    }

    private static void validateVoid(Collection<TransactionInput> existingTransactions, TransactionInput voidTx) {
        if (voidTx.voidsTransactionId() == null) {
            throw new PortfolioValidationException(ReasonCode.INVALID_TRANSACTION_TYPE, "VOID requires a target transaction id");
        }

        TransactionInput target = existingTransactions.stream()
                .filter(t -> t.id().equals(voidTx.voidsTransactionId()))
                .findFirst()
                .orElseThrow(() -> new PortfolioValidationException(ReasonCode.TRANSACTION_NOT_FOUND, "Target transaction not found"));

        if (target.type() == TransactionType.VOID) {
            throw new PortfolioValidationException(ReasonCode.CANNOT_VOID_VOID, "Cannot void a VOID transaction");
        }

        // Check if already voided
        boolean alreadyVoided = existingTransactions.stream()
                .anyMatch(t -> t.type() == TransactionType.VOID && target.id().equals(t.voidsTransactionId()));
        if (alreadyVoided) {
            throw new PortfolioValidationException(ReasonCode.ALREADY_VOIDED, "Target transaction is already voided");
        }

        // If target is BUY, ensure its lot has not been consumed by any non-voided SELL
        if (target.type() == TransactionType.BUY) {
            PortfolioHoldingsState state = replayHoldings(existingTransactions, Collections.emptyMap());
            FifoLot matchingLot = state.openLots().stream()
                    .filter(lot -> lot.transactionId().equals(target.id()))
                    .findFirst()
                    .orElse(null);

            if (matchingLot == null || matchingLot.quantityRemaining().compareTo(target.quantity()) < 0) {
                throw new PortfolioValidationException(ReasonCode.LOT_ALREADY_CONSUMED,
                        "Cannot void BUY because its lot has been partially or fully consumed by a later SELL");
            }
        }

        // Simulate remaining ledger without the target to guarantee no dependent transactions are broken
        List<TransactionInput> simulatedRemaining = existingTransactions.stream()
                .filter(t -> !t.id().equals(target.id()))
                .toList();

        BigDecimal runningCash = BigDecimal.ZERO;
        Map<UUID, BigDecimal> runningHoldings = new HashMap<>();
        for (TransactionInput tx : filterAndSortChronologically(simulatedRemaining)) {
            switch (tx.type()) {
                case DEPOSIT -> runningCash = runningCash.add(tx.amount());
                case WITHDRAW -> {
                    if (tx.amount().compareTo(runningCash) > 0) {
                        throw new PortfolioValidationException(ReasonCode.INSUFFICIENT_CASH_BALANCE,
                                "Cannot void target because a subsequent WITHDRAW depends on its cash balance");
                    }
                    runningCash = runningCash.subtract(tx.amount());
                }
                case BUY -> {
                    BigDecimal cost = tx.quantity().multiply(tx.price()).add(tx.fee());
                    if (cost.compareTo(runningCash) > 0) {
                        throw new PortfolioValidationException(ReasonCode.INSUFFICIENT_CASH_BALANCE,
                                "Cannot void target because a subsequent BUY depends on its cash balance");
                    }
                    runningCash = runningCash.subtract(cost);
                    runningHoldings.merge(tx.instrumentId(), tx.quantity(), BigDecimal::add);
                }
                case SELL -> {
                    BigDecimal available = runningHoldings.getOrDefault(tx.instrumentId(), BigDecimal.ZERO);
                    if (tx.quantity().compareTo(available) > 0) {
                        throw new PortfolioValidationException(ReasonCode.LOT_ALREADY_CONSUMED,
                                "Cannot void target because a subsequent SELL depends on this position");
                    }
                    runningHoldings.put(tx.instrumentId(), available.subtract(tx.quantity()));
                    BigDecimal proceeds = tx.quantity().multiply(tx.price()).subtract(tx.fee());
                    runningCash = runningCash.add(proceeds);
                }
                case VOID -> {}
            }
        }
    }

    private static void validateSell(Collection<TransactionInput> existingTransactions, TransactionInput sellTx) {
        if (sellTx.quantity() == null || sellTx.quantity().signum() <= 0) {
            throw new PortfolioValidationException(ReasonCode.INVALID_TRANSACTION_TYPE, "SELL quantity must be positive");
        }
        if (sellTx.price() == null || sellTx.price().signum() <= 0) {
            throw new PortfolioValidationException(ReasonCode.INVALID_TRANSACTION_TYPE, "SELL price must be positive");
        }

        // Replay transactions strictly before/at sellTx.executedAt
        List<TransactionInput> priorTransactions = existingTransactions.stream()
                .filter(t -> t.executedAt().isBefore(sellTx.executedAt())
                        || (t.executedAt().equals(sellTx.executedAt()) && t.sequenceNo() < sellTx.sequenceNo()))
                .toList();

        PortfolioHoldingsState state = replayHoldings(priorTransactions, Collections.emptyMap());
        PositionResult pos = state.positions().get(sellTx.instrumentId());
        BigDecimal availableQty = pos != null ? pos.quantity() : BigDecimal.ZERO;

        if (sellTx.quantity().compareTo(availableQty) > 0) {
            throw new PortfolioValidationException(ReasonCode.INSUFFICIENT_POSITION,
                    "Insufficient position: available " + availableQty + ", requested " + sellTx.quantity());
        }
    }

    private static void validateWithdraw(Collection<TransactionInput> existingTransactions, TransactionInput withdrawTx) {
        if (withdrawTx.amount() == null || withdrawTx.amount().signum() <= 0) {
            throw new PortfolioValidationException(ReasonCode.INVALID_TRANSACTION_TYPE, "WITHDRAW amount must be positive");
        }

        List<TransactionInput> priorTransactions = existingTransactions.stream()
                .filter(t -> t.executedAt().isBefore(withdrawTx.executedAt())
                        || (t.executedAt().equals(withdrawTx.executedAt()) && t.sequenceNo() < withdrawTx.sequenceNo()))
                .toList();

        PortfolioHoldingsState state = replayHoldings(priorTransactions, Collections.emptyMap());
        BigDecimal cash = state.cashBalance();

        if (withdrawTx.amount().compareTo(cash) > 0) {
            throw new PortfolioValidationException(ReasonCode.INSUFFICIENT_CASH_BALANCE,
                    "Insufficient cash balance: available " + cash + ", requested " + withdrawTx.amount());
        }
    }

    // --- Core Replay & Calculation Engine ---

    /**
     * Filters out voided transactions and VOID records, then sorts chronologically per rule U-3.
     */
    public static List<TransactionInput> filterAndSortChronologically(Collection<TransactionInput> transactions) {
        Set<UUID> voidedIds = new HashSet<>();
        for (TransactionInput tx : transactions) {
            if (tx.type() == TransactionType.VOID && tx.voidsTransactionId() != null) {
                voidedIds.add(tx.voidsTransactionId());
            }
        }

        return transactions.stream()
                .filter(tx -> tx.type() != TransactionType.VOID && !voidedIds.contains(tx.id()))
                .sorted(CHRONOLOGICAL_ORDER)
                .toList();
    }

    /**
     * Replays non-voided transactions to derive open positions, FIFO cost basis,
     * realized P/L, cash balance, and totals.
     */
    public static PortfolioHoldingsState replayHoldings(
            Collection<TransactionInput> transactions,
            Map<UUID, BigDecimal> currentPrices) {
        return replayHoldings(transactions, currentPrices, Collections.emptyMap());
    }

    public static PortfolioHoldingsState replayHoldings(
            Collection<TransactionInput> transactions,
            Map<UUID, BigDecimal> currentPrices,
            Map<UUID, String> symbols) {

        List<TransactionInput> activeTx = filterAndSortChronologically(transactions);

        BigDecimal cashBalance = BigDecimal.ZERO;
        BigDecimal netContributedCapital = BigDecimal.ZERO;

        Map<UUID, List<FifoLot>> lotsByInstrument = new LinkedHashMap<>();
        Map<UUID, BigDecimal> realizedPLByInstrument = new LinkedHashMap<>();
        Map<UUID, String> resolvedSymbols = new HashMap<>(symbols);

        for (TransactionInput tx : activeTx) {
            if (tx.instrumentId() != null && tx.symbol() != null) {
                resolvedSymbols.putIfAbsent(tx.instrumentId(), tx.symbol());
            }

            switch (tx.type()) {
                case DEPOSIT -> {
                    cashBalance = cashBalance.add(tx.amount());
                    netContributedCapital = netContributedCapital.add(tx.amount());
                }
                case WITHDRAW -> {
                    cashBalance = cashBalance.subtract(tx.amount());
                    netContributedCapital = netContributedCapital.subtract(tx.amount());
                }
                case BUY -> {
                    BigDecimal totalCost = tx.quantity().multiply(tx.price()).add(tx.fee());
                    cashBalance = cashBalance.subtract(totalCost);

                    BigDecimal unitCost = tx.price().add(
                            tx.fee().divide(tx.quantity(), INTERNAL_SCALE, RoundingMode.HALF_UP));

                    FifoLot lot = new FifoLot(
                            tx.id(),
                            tx.instrumentId(),
                            tx.quantity(),
                            tx.quantity(),
                            unitCost,
                            tx.executedAt(),
                            tx.sequenceNo());

                    lotsByInstrument.computeIfAbsent(tx.instrumentId(), k -> new ArrayList<>()).add(lot);
                }
                case SELL -> {
                    BigDecimal netProceeds = tx.quantity().multiply(tx.price()).subtract(tx.fee());
                    cashBalance = cashBalance.add(netProceeds);

                    BigDecimal sellNetPrice = tx.price().subtract(
                            tx.fee().divide(tx.quantity(), INTERNAL_SCALE, RoundingMode.HALF_UP));

                    List<FifoLot> lots = lotsByInstrument.getOrDefault(tx.instrumentId(), Collections.emptyList());
                    BigDecimal remainingToSell = tx.quantity();
                    BigDecimal txRealizedPL = BigDecimal.ZERO;

                    List<FifoLot> updatedLots = new ArrayList<>();
                    for (FifoLot lot : lots) {
                        if (remainingToSell.signum() == 0) {
                            updatedLots.add(lot);
                            continue;
                        }

                        if (lot.quantityRemaining().compareTo(remainingToSell) <= 0) {
                            BigDecimal qtySold = lot.quantityRemaining();
                            BigDecimal lotPL = qtySold.multiply(sellNetPrice.subtract(lot.unitCost()));
                            txRealizedPL = txRealizedPL.add(lotPL);
                            remainingToSell = remainingToSell.subtract(qtySold);
                            // lot is fully consumed, quantityRemaining becomes 0 (not added to updatedLots)
                        } else {
                            BigDecimal qtySold = remainingToSell;
                            BigDecimal lotPL = qtySold.multiply(sellNetPrice.subtract(lot.unitCost()));
                            txRealizedPL = txRealizedPL.add(lotPL);

                            FifoLot partiallyConsumedLot = new FifoLot(
                                    lot.transactionId(),
                                    lot.instrumentId(),
                                    lot.quantityRemaining().subtract(qtySold),
                                    lot.initialQuantity(),
                                    lot.unitCost(),
                                    lot.executedAt(),
                                    lot.sequenceNo());
                            updatedLots.add(partiallyConsumedLot);
                            remainingToSell = BigDecimal.ZERO;
                        }
                    }

                    lotsByInstrument.put(tx.instrumentId(), updatedLots);
                    BigDecimal prevRealized = realizedPLByInstrument.getOrDefault(tx.instrumentId(), BigDecimal.ZERO);
                    realizedPLByInstrument.put(tx.instrumentId(), prevRealized.add(txRealizedPL));
                }
                case VOID -> {
                    // Filtered out beforehand
                }
            }
        }

        // Build position results
        Map<UUID, PositionResult> positions = new LinkedHashMap<>();
        List<FifoLot> allOpenLots = new ArrayList<>();

        Set<UUID> allInstrumentIds = new HashSet<>();
        allInstrumentIds.addAll(lotsByInstrument.keySet());
        allInstrumentIds.addAll(realizedPLByInstrument.keySet());

        BigDecimal totalPositionsValue = BigDecimal.ZERO;
        BigDecimal totalUnrealizedPL = BigDecimal.ZERO;
        BigDecimal totalRealizedPL = BigDecimal.ZERO;

        for (UUID instrumentId : allInstrumentIds) {
            List<FifoLot> lots = lotsByInstrument.getOrDefault(instrumentId, Collections.emptyList());
            BigDecimal openQty = BigDecimal.ZERO;
            BigDecimal totalLotCost = BigDecimal.ZERO;

            for (FifoLot lot : lots) {
                if (lot.quantityRemaining().signum() > 0) {
                    openQty = openQty.add(lot.quantityRemaining());
                    totalLotCost = totalLotCost.add(lot.quantityRemaining().multiply(lot.unitCost()));
                    allOpenLots.add(lot);
                }
            }

            BigDecimal avgCostBasis = openQty.signum() > 0
                    ? totalLotCost.divide(openQty, INTERNAL_SCALE, RoundingMode.HALF_UP)
                    : null;

            BigDecimal currentPrice = currentPrices.get(instrumentId);
            boolean priceAvail = currentPrice != null;

            BigDecimal marketValue = null;
            BigDecimal unrealizedPL = null;
            BigDecimal unrealizedPLPercent = null;

            if (openQty.signum() > 0 && priceAvail) {
                marketValue = openQty.multiply(currentPrice);
                totalPositionsValue = totalPositionsValue.add(marketValue);

                unrealizedPL = openQty.multiply(currentPrice.subtract(avgCostBasis));
                totalUnrealizedPL = totalUnrealizedPL.add(unrealizedPL);

                if (avgCostBasis.signum() > 0) {
                    unrealizedPLPercent = currentPrice.subtract(avgCostBasis)
                            .divide(avgCostBasis, INTERNAL_SCALE, RoundingMode.HALF_UP);
                }
            }

            BigDecimal instrumentRealizedPL = realizedPLByInstrument.getOrDefault(instrumentId, BigDecimal.ZERO);
            totalRealizedPL = totalRealizedPL.add(instrumentRealizedPL);

            // Create position if open quantity > 0 OR if there was any activity (realizedPL)
            if (openQty.signum() > 0 || instrumentRealizedPL.signum() != 0) {
                positions.put(instrumentId, new PositionResult(
                        instrumentId,
                        resolvedSymbols.get(instrumentId),
                        openQty,
                        avgCostBasis,
                        currentPrice,
                        marketValue,
                        unrealizedPL,
                        unrealizedPLPercent,
                        instrumentRealizedPL,
                        null, // populated in second pass
                        priceAvail));
            }
        }

        BigDecimal totalValue = totalPositionsValue.add(cashBalance);

        // Second pass for allocation
        Map<UUID, PositionResult> finalizedPositions = new LinkedHashMap<>();
        for (Map.Entry<UUID, PositionResult> entry : positions.entrySet()) {
            PositionResult p = entry.getValue();
            BigDecimal allocation = null;
            if (p.quantity().signum() > 0 && p.marketValue() != null && totalValue.signum() > 0) {
                allocation = p.marketValue().divide(totalValue, INTERNAL_SCALE, RoundingMode.HALF_UP);
            }
            finalizedPositions.put(entry.getKey(), new PositionResult(
                    p.instrumentId(),
                    p.symbol(),
                    p.quantity(),
                    p.averageCostBasis(),
                    p.currentPrice(),
                    p.marketValue(),
                    p.unrealizedPL(),
                    p.unrealizedPLPercent(),
                    p.realizedPL(),
                    allocation,
                    p.priceAvailable()));
        }

        PortfolioTotalsResult totals = new PortfolioTotalsResult(
                totalValue,
                totalPositionsValue,
                cashBalance,
                totalUnrealizedPL,
                totalRealizedPL,
                netContributedCapital);

        return new PortfolioHoldingsState(
                finalizedPositions,
                totals,
                allOpenLots,
                cashBalance,
                netContributedCapital);
    }

    // --- Return Analytics ---

    /**
     * Computes return since inception and return over a period [T0, T1] per research R-005.
     */
    public static ReturnResult calculateReturns(
            Collection<TransactionInput> allTransactions,
            BigDecimal currentTotalValue,
            LocalDate periodFrom,
            LocalDate periodTo,
            BigDecimal totalValueAtT0,
            BigDecimal totalValueAtT1) {

        List<TransactionInput> activeTx = filterAndSortChronologically(allTransactions);

        // Inception return
        BigDecimal netContributedCapitalNow = BigDecimal.ZERO;
        for (TransactionInput tx : activeTx) {
            if (tx.type() == TransactionType.DEPOSIT) {
                netContributedCapitalNow = netContributedCapitalNow.add(tx.amount());
            } else if (tx.type() == TransactionType.WITHDRAW) {
                netContributedCapitalNow = netContributedCapitalNow.subtract(tx.amount());
            }
        }

        BigDecimal returnSinceInception = null;
        if (netContributedCapitalNow.signum() > 0 && currentTotalValue != null) {
            returnSinceInception = currentTotalValue.subtract(netContributedCapitalNow)
                    .divide(netContributedCapitalNow, INTERNAL_SCALE, RoundingMode.HALF_UP);
        }

        // Period return
        BigDecimal returnOverPeriod = null;
        if (periodFrom != null && periodTo != null && totalValueAtT0 != null && totalValueAtT1 != null) {
            BigDecimal netContributedPeriod = BigDecimal.ZERO;
            for (TransactionInput tx : activeTx) {
                LocalDate txDate = tx.executedAt().atZone(MARKET_ZONE).toLocalDate();
                if (txDate.isAfter(periodFrom) && !txDate.isAfter(periodTo)) {
                    if (tx.type() == TransactionType.DEPOSIT) {
                        netContributedPeriod = netContributedPeriod.add(tx.amount());
                    } else if (tx.type() == TransactionType.WITHDRAW) {
                        netContributedPeriod = netContributedPeriod.subtract(tx.amount());
                    }
                }
            }

            BigDecimal denom = totalValueAtT0.add(netContributedPeriod);
            if (denom.signum() > 0) {
                returnOverPeriod = totalValueAtT1.subtract(totalValueAtT0).subtract(netContributedPeriod)
                        .divide(denom, INTERNAL_SCALE, RoundingMode.HALF_UP);
            }
        }

        boolean available = returnSinceInception != null || returnOverPeriod != null;
        return new ReturnResult(returnSinceInception, returnOverPeriod, DISCLOSURE_CODE, available);
    }

    // --- Performance History & Drawdown (Research R-006) ---

    public static PerformanceHistoryResult calculatePerformanceHistory(
            Collection<TransactionInput> allTransactions,
            List<LocalDate> tradingDates,
            Map<UUID, Map<LocalDate, BigDecimal>> dailyClosesByInstrument,
            LocalDate requestedFrom,
            LocalDate requestedTo) {

        List<TransactionInput> activeTx = filterAndSortChronologically(allTransactions);
        if (activeTx.isEmpty() || tradingDates.isEmpty()) {
            return new PerformanceHistoryResult(
                    requestedFrom, requestedTo, false, BigDecimal.ZERO, Collections.emptyList());
        }

        LocalDate inceptionDate = activeTx.get(0).executedAt().atZone(MARKET_ZONE).toLocalDate();
        boolean clamped = requestedFrom != null && requestedFrom.isBefore(inceptionDate);
        LocalDate resolvedFrom = clamped ? inceptionDate : (requestedFrom != null ? requestedFrom : inceptionDate);
        LocalDate resolvedTo = requestedTo != null ? requestedTo : tradingDates.get(tradingDates.size() - 1);

        List<LocalDate> periodDates = tradingDates.stream()
                .filter(d -> !d.isBefore(resolvedFrom) && !d.isAfter(resolvedTo))
                .sorted()
                .toList();

        if (periodDates.isEmpty()) {
            return new PerformanceHistoryResult(
                    resolvedFrom, resolvedTo, clamped, BigDecimal.ZERO, Collections.emptyList());
        }

        List<DrawdownPoint> series = new ArrayList<>();
        BigDecimal peakValue = BigDecimal.ZERO;
        BigDecimal maxDrawdown = BigDecimal.ZERO;

        Map<UUID, BigDecimal> lastKnownCloses = new HashMap<>();

        for (LocalDate date : periodDates) {
            // Find all transactions executed on or before end of this trading date
            List<TransactionInput> asOfTx = activeTx.stream()
                    .filter(tx -> !tx.executedAt().atZone(MARKET_ZONE).toLocalDate().isAfter(date))
                    .toList();

            // Resolve prices on this date
            Map<UUID, BigDecimal> datePrices = new HashMap<>();
            boolean isPartial = false;
            String partialSymbol = null;

            // Get all instruments actually open (quantity > 0) as of this date — a
            // fully-closed position's price is irrelevant to this date's valuation
            // and must not trigger a spurious PARTIAL flag for an unrelated gap.
            PortfolioHoldingsState intermediateState = replayHoldings(asOfTx, Collections.emptyMap());
            for (PositionResult openPos : intermediateState.positions().values()) {
                if (openPos.quantity().signum() <= 0) {
                    continue;
                }
                UUID instId = openPos.instrumentId();
                Map<LocalDate, BigDecimal> instCloses = dailyClosesByInstrument.getOrDefault(instId, Collections.emptyMap());
                BigDecimal close = instCloses.get(date);
                if (close != null) {
                    lastKnownCloses.put(instId, close);
                    datePrices.put(instId, close);
                } else if (lastKnownCloses.containsKey(instId)) {
                    // Forward fill
                    datePrices.put(instId, lastKnownCloses.get(instId));
                    isPartial = true;
                    if (partialSymbol == null) {
                        partialSymbol = openPos.symbol();
                    }
                }
            }

            PortfolioHoldingsState valuedState = replayHoldings(asOfTx, datePrices);
            BigDecimal dateTotalValue = valuedState.totals().totalValue();

            if (dateTotalValue.compareTo(peakValue) > 0) {
                peakValue = dateTotalValue;
            }

            BigDecimal dd = BigDecimal.ZERO;
            if (peakValue.signum() > 0 && dateTotalValue.compareTo(peakValue) < 0) {
                dd = peakValue.subtract(dateTotalValue).divide(peakValue, INTERNAL_SCALE, RoundingMode.HALF_UP);
            }

            if (dd.compareTo(maxDrawdown) > 0) {
                maxDrawdown = dd;
            }

            series.add(new DrawdownPoint(date, dateTotalValue, peakValue, dd, isPartial, partialSymbol));
        }

        return new PerformanceHistoryResult(resolvedFrom, resolvedTo, clamped, maxDrawdown, series);
    }

    // --- Concentration (Research R-007) ---

    public static List<StockConcentrationItem> calculateStockConcentration(PortfolioHoldingsState state) {
        BigDecimal totalValue = state.totals().totalValue();
        List<StockConcentrationItem> items = new ArrayList<>();

        for (PositionResult pos : state.positions().values()) {
            if (pos.quantity().signum() > 0 && pos.marketValue() != null) {
                BigDecimal weight = totalValue.signum() > 0
                        ? pos.marketValue().divide(totalValue, INTERNAL_SCALE, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                items.add(new StockConcentrationItem(pos.instrumentId(), pos.symbol(), pos.marketValue(), weight));
            }
        }

        items.sort(Comparator.comparing(StockConcentrationItem::weight).reversed());
        return items;
    }

    public static List<SectorConcentrationItem> calculateSectorConcentration(
            PortfolioHoldingsState state,
            Map<UUID, SectorInfo> sectorByInstrument) {
        BigDecimal totalValue = state.totals().totalValue();

        Map<String, SectorAccumulator> accMap = new HashMap<>();

        for (PositionResult pos : state.positions().values()) {
            if (pos.quantity().signum() > 0 && pos.marketValue() != null) {
                SectorInfo info = sectorByInstrument.get(pos.instrumentId());
                String key = info != null && info.sectorDisplayNameEn() != null ? info.sectorDisplayNameEn() : "Unclassified";
                String viName = info != null ? info.sectorDisplayNameVi() : "Chưa phân loại";
                String enName = info != null ? info.sectorDisplayNameEn() : "Unclassified";

                SectorAccumulator acc = accMap.computeIfAbsent(key, k -> new SectorAccumulator(viName, enName));
                acc.marketValue = acc.marketValue.add(pos.marketValue());
            }
        }

        List<SectorConcentrationItem> items = new ArrayList<>();
        for (SectorAccumulator acc : accMap.values()) {
            BigDecimal weight = totalValue.signum() > 0
                    ? acc.marketValue.divide(totalValue, INTERNAL_SCALE, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            items.add(new SectorConcentrationItem(acc.viName, acc.enName, acc.marketValue, weight));
        }

        items.sort(Comparator.comparing(SectorConcentrationItem::weight).reversed());
        return items;
    }

    public record SectorInfo(String sectorDisplayNameVi, String sectorDisplayNameEn) {
    }

    private static class SectorAccumulator {
        final String viName;
        final String enName;
        BigDecimal marketValue = BigDecimal.ZERO;

        SectorAccumulator(String viName, String enName) {
            this.viName = viName;
            this.enName = enName;
        }
    }

    // --- Risk Exposure Rollup (Research R-008) ---

    public static RiskExposureResult calculateRiskExposure(
            PortfolioHoldingsState state,
            Map<UUID, List<SignalRiskInput>> signalsByInstrument) {

        BigDecimal totalPositionsValue = state.totals().totalPositionsValue();
        BigDecimal coveredPositionsValue = BigDecimal.ZERO;
        BigDecimal weightedRiskSum = BigDecimal.ZERO;

        for (PositionResult pos : state.positions().values()) {
            if (pos.quantity().signum() > 0 && pos.marketValue() != null) {
                List<SignalRiskInput> signals = signalsByInstrument.getOrDefault(pos.instrumentId(), Collections.emptyList());
                if (!signals.isEmpty()) {
                    // Position risk contribution = max risk_score among current signals
                    int maxRisk = signals.stream()
                            .map(SignalRiskInput::riskScore)
                            .filter(Objects::nonNull)
                            .max(Integer::compareTo)
                            .orElse(0);

                    coveredPositionsValue = coveredPositionsValue.add(pos.marketValue());
                    weightedRiskSum = weightedRiskSum.add(pos.marketValue().multiply(BigDecimal.valueOf(maxRisk)));
                }
            }
        }

        if (coveredPositionsValue.signum() == 0) {
            BigDecimal coverageRatio = totalPositionsValue.signum() > 0
                    ? BigDecimal.ZERO
                    : BigDecimal.ZERO;
            return new RiskExposureResult(null, null, coverageRatio, BigDecimal.ZERO, totalPositionsValue);
        }

        BigDecimal riskScoreDec = weightedRiskSum.divide(coveredPositionsValue, 0, RoundingMode.HALF_UP);
        int score = riskScoreDec.intValue();

        String level;
        if (score <= 33) {
            level = "LOW";
        } else if (score <= 66) {
            level = "MEDIUM";
        } else {
            level = "HIGH";
        }

        BigDecimal coverageRatio = totalPositionsValue.signum() > 0
                ? coveredPositionsValue.divide(totalPositionsValue, INTERNAL_SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new RiskExposureResult(score, level, coverageRatio, coveredPositionsValue, totalPositionsValue);
    }

    // --- Benchmark Comparison ---

    public static BenchmarkComparisonResult calculateBenchmarkComparison(
            LocalDate periodFrom,
            LocalDate periodTo,
            BigDecimal portfolioReturn,
            BigDecimal indexCloseT0,
            BigDecimal indexCloseT1) {

        BigDecimal benchmarkReturn = null;
        BigDecimal alpha = null;

        if (indexCloseT0 != null && indexCloseT1 != null && indexCloseT0.signum() > 0) {
            benchmarkReturn = indexCloseT1.subtract(indexCloseT0)
                    .divide(indexCloseT0, INTERNAL_SCALE, RoundingMode.HALF_UP);

            if (portfolioReturn != null) {
                alpha = portfolioReturn.subtract(benchmarkReturn);
            }
        }

        return new BenchmarkComparisonResult(periodFrom, periodTo, portfolioReturn, benchmarkReturn, alpha);
    }
}

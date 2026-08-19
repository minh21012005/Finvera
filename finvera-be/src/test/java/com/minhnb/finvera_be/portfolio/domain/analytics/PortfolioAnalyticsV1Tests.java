package com.minhnb.finvera_be.portfolio.domain.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioAnalyticsV1.BenchmarkComparisonResult;
import com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioAnalyticsV1.PerformanceHistoryResult;
import com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioAnalyticsV1.PortfolioHoldingsState;
import com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioAnalyticsV1.PositionResult;
import com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioAnalyticsV1.ReturnResult;
import com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioAnalyticsV1.RiskExposureResult;
import com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioAnalyticsV1.SectorConcentrationItem;
import com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioAnalyticsV1.SectorInfo;
import com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioAnalyticsV1.SignalRiskInput;
import com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioAnalyticsV1.StockConcentrationItem;
import com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioAnalyticsV1.TransactionInput;
import com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioValidationException.ReasonCode;
import com.minhnb.finvera_be.portfolio.domain.model.PortfolioTypes.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PortfolioAnalyticsV1Tests {

    private static final UUID FPT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID VNM_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID HPG_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    @DisplayName("Vector 1: Simple DEPOSIT + BUY + partial SELL at a different price -> correct remaining qty, avg cost basis, unrealized/realized PL")
    void simpleDepositBuyAndPartialSell() {
        List<TransactionInput> txs = List.of(
                deposit(UUID.randomUUID(), 1, "100000000", Instant.parse("2026-08-01T10:00:00Z")),
                buy(UUID.randomUUID(), 2, FPT_ID, "FPT", "1000", "50000", "50000", Instant.parse("2026-08-02T10:00:00Z")), // cost = 50,050,000, unitCost = 50,050
                sell(UUID.randomUUID(), 3, FPT_ID, "FPT", "400", "60000", "20000", Instant.parse("2026-08-05T10:00:00Z")) // proceeds = 23,980,000, sellNetPrice = 59,950
        );

        // Price at evaluation = 65,000
        Map<UUID, BigDecimal> prices = Map.of(FPT_ID, new BigDecimal("65000"));
        PortfolioHoldingsState state = PortfolioAnalyticsV1.replayHoldings(txs, prices);

        // Cash: 100,000,000 - 50,050,000 + 23,980,000 = 73,930,000
        assertThat(state.cashBalance()).isEqualByComparingTo("73930000");

        PositionResult fpt = state.positions().get(FPT_ID);
        assertThat(fpt).isNotNull();
        // Remaining qty: 1000 - 400 = 600
        assertThat(fpt.quantity()).isEqualByComparingTo("600");
        // Avg cost basis: 50,050
        assertThat(fpt.averageCostBasis()).isEqualByComparingTo("50050");
        // Market value: 600 * 65,000 = 39,000,000
        assertThat(fpt.marketValue()).isEqualByComparingTo("39000000");
        // Unrealized PL: 600 * (65,000 - 50,050) = 600 * 14,950 = 8,970,000
        assertThat(fpt.unrealizedPL()).isEqualByComparingTo("8970000");
        // Realized PL on 400 sold: 400 * (59,950 - 50,050) = 400 * 9,900 = 3,960,000
        assertThat(fpt.realizedPL()).isEqualByComparingTo("3960000");

        // Total value = 39,000,000 + 73,930,000 = 112,930,000
        assertThat(state.totals().totalValue()).isEqualByComparingTo("112930000");
    }

    @Test
    @DisplayName("Vector 2: SELL exceeding held quantity is rejected with INSUFFICIENT_POSITION")
    void sellExceedingHeldQuantityRejected() {
        List<TransactionInput> existing = List.of(
                deposit(UUID.randomUUID(), 1, "50000000", Instant.parse("2026-08-01T10:00:00Z")),
                buy(UUID.randomUUID(), 2, FPT_ID, "FPT", "500", "50000", "0", Instant.parse("2026-08-02T10:00:00Z"))
        );

        TransactionInput excessiveSell = sell(UUID.randomUUID(), 3, FPT_ID, "FPT", "600", "55000", "0", Instant.parse("2026-08-03T10:00:00Z"));

        assertThatThrownBy(() -> PortfolioAnalyticsV1.validateTransaction(existing, excessiveSell))
                .isInstanceOf(PortfolioValidationException.class)
                .satisfies(e -> assertThat(((PortfolioValidationException) e).getReasonCode()).isEqualTo(ReasonCode.INSUFFICIENT_POSITION));
    }

    @Test
    @DisplayName("Vector 3: WITHDRAW exceeding cash balance is rejected with INSUFFICIENT_CASH_BALANCE")
    void withdrawExceedingCashRejected() {
        List<TransactionInput> existing = List.of(
                deposit(UUID.randomUUID(), 1, "10000000", Instant.parse("2026-08-01T10:00:00Z")),
                buy(UUID.randomUUID(), 2, FPT_ID, "FPT", "100", "50000", "0", Instant.parse("2026-08-02T10:00:00Z")) // spent 5,000,000, cash left 5,000,000
        );

        TransactionInput excessiveWithdraw = withdraw(UUID.randomUUID(), 3, "6000000", Instant.parse("2026-08-03T10:00:00Z"));

        assertThatThrownBy(() -> PortfolioAnalyticsV1.validateTransaction(existing, excessiveWithdraw))
                .isInstanceOf(PortfolioValidationException.class)
                .satisfies(e -> assertThat(((PortfolioValidationException) e).getReasonCode()).isEqualTo(ReasonCode.INSUFFICIENT_CASH_BALANCE));
    }

    @Test
    @DisplayName("Vector 4: Backdated BUY inserted between two already-recorded transactions reorders replay by executed_at")
    void backdatedBuyReordersReplay() {
        UUID buy1Id = UUID.randomUUID();
        UUID sell1Id = UUID.randomUUID();
        UUID backdatedBuyId = UUID.randomUUID();

        // Let's create transactions out of insertion order
        TransactionInput tx1 = deposit(UUID.randomUUID(), 1, "100000000", Instant.parse("2026-08-01T10:00:00Z"));
        TransactionInput tx2 = buy(buy1Id, 2, FPT_ID, "FPT", "500", "50000", "0", Instant.parse("2026-08-02T10:00:00Z")); // Lot 1: 500 @ 50k
        TransactionInput tx3 = sell(sell1Id, 3, FPT_ID, "FPT", "600", "60000", "0", Instant.parse("2026-08-10T10:00:00Z")); // Sell 600 @ 60k
        // Backdated BUY executed on 2026-08-05 (between tx2 and tx3) but with sequenceNo 4
        TransactionInput backdatedBuy = buy(backdatedBuyId, 4, FPT_ID, "FPT", "500", "52000", "0", Instant.parse("2026-08-05T10:00:00Z")); // Lot 2: 500 @ 52k

        List<TransactionInput> allTxs = List.of(tx1, tx2, tx3, backdatedBuy);

        PortfolioHoldingsState state = PortfolioAnalyticsV1.replayHoldings(allTxs, Map.of(FPT_ID, new BigDecimal("65000")));

        PositionResult fpt = state.positions().get(FPT_ID);
        // Total bought = 1000, sold = 600, remaining = 400
        assertThat(fpt.quantity()).isEqualByComparingTo("400");
        // Replay consumed: 500 from Lot 1 (@50k) and 100 from Lot 2 (@52k)
        // Remaining 400 is from Lot 2 (@52k)
        assertThat(fpt.averageCostBasis()).isEqualByComparingTo("52000");

        // Realized PL: (500 * (60k - 50k)) + (100 * (60k - 52k)) = 5,000,000 + 800,000 = 5,800,000
        assertThat(fpt.realizedPL()).isEqualByComparingTo("5800000");
    }

    @Test
    @DisplayName("Vector 5: VOID of a BUY with no dependent SELL removes it cleanly from replay")
    void voidOfCleanBuy() {
        UUID buyId = UUID.randomUUID();
        UUID voidId = UUID.randomUUID();

        List<TransactionInput> txs = List.of(
                deposit(UUID.randomUUID(), 1, "50000000", Instant.parse("2026-08-01T10:00:00Z")),
                buy(buyId, 2, FPT_ID, "FPT", "500", "50000", "0", Instant.parse("2026-08-02T10:00:00Z")),
                voidTx(voidId, 3, buyId, "Entered wrong symbol", Instant.parse("2026-08-03T10:00:00Z"))
        );

        PortfolioHoldingsState state = PortfolioAnalyticsV1.replayHoldings(txs, Map.of(FPT_ID, new BigDecimal("60000")));

        // FPT position should not exist or be 0
        PositionResult fpt = state.positions().get(FPT_ID);
        assertThat(fpt).isNull();
        // Cash should be full 50,000,000
        assertThat(state.cashBalance()).isEqualByComparingTo("50000000");
    }

    @Test
    @DisplayName("Vector 6: VOID of a BUY whose lot was already partially sold is rejected with LOT_ALREADY_CONSUMED")
    void voidOfPartiallyConsumedBuyRejected() {
        UUID buyId = UUID.randomUUID();

        List<TransactionInput> existing = List.of(
                deposit(UUID.randomUUID(), 1, "50000000", Instant.parse("2026-08-01T10:00:00Z")),
                buy(buyId, 2, FPT_ID, "FPT", "500", "50000", "0", Instant.parse("2026-08-02T10:00:00Z")),
                sell(UUID.randomUUID(), 3, FPT_ID, "FPT", "200", "60000", "0", Instant.parse("2026-08-03T10:00:00Z"))
        );

        TransactionInput badVoid = voidTx(UUID.randomUUID(), 4, buyId, "Cancel buy", Instant.parse("2026-08-04T10:00:00Z"));

        assertThatThrownBy(() -> PortfolioAnalyticsV1.validateTransaction(existing, badVoid))
                .isInstanceOf(PortfolioValidationException.class)
                .satisfies(e -> assertThat(((PortfolioValidationException) e).getReasonCode()).isEqualTo(ReasonCode.LOT_ALREADY_CONSUMED));
    }

    @Test
    @DisplayName("Vector 7 & 8: VOID of already-voided transaction and VOID of a VOID are rejected")
    void voidOfAlreadyVoidedOrVoidRejected() {
        UUID depId = UUID.randomUUID();
        UUID voidId = UUID.randomUUID();

        List<TransactionInput> existing = List.of(
                deposit(depId, 1, "10000000", Instant.parse("2026-08-01T10:00:00Z")),
                voidTx(voidId, 2, depId, "Mistake", Instant.parse("2026-08-02T10:00:00Z"))
        );

        // Try to void the deposit again
        TransactionInput doubleVoid = voidTx(UUID.randomUUID(), 3, depId, "Double void", Instant.parse("2026-08-03T10:00:00Z"));
        assertThatThrownBy(() -> PortfolioAnalyticsV1.validateTransaction(existing, doubleVoid))
                .isInstanceOf(PortfolioValidationException.class)
                .satisfies(e -> assertThat(((PortfolioValidationException) e).getReasonCode()).isEqualTo(ReasonCode.ALREADY_VOIDED));

        // Try to void the VOID transaction
        TransactionInput voidOfVoid = voidTx(UUID.randomUUID(), 4, voidId, "Void of void", Instant.parse("2026-08-04T10:00:00Z"));
        assertThatThrownBy(() -> PortfolioAnalyticsV1.validateTransaction(existing, voidOfVoid))
                .isInstanceOf(PortfolioValidationException.class)
                .satisfies(e -> assertThat(((PortfolioValidationException) e).getReasonCode()).isEqualTo(ReasonCode.CANNOT_VOID_VOID));
    }

    @Test
    @DisplayName("Vector 9: Two transactions with identical executed_at have deterministic sequence_no tie-break applied")
    void sameExecutedAtTieBreakBySequenceNo() {
        Instant sameInstant = Instant.parse("2026-08-01T10:00:00Z");

        // Buy 1 @ 50k (seq 2), Buy 2 @ 60k (seq 3)
        List<TransactionInput> txs = List.of(
                deposit(UUID.randomUUID(), 1, "100000000", sameInstant),
                buy(UUID.randomUUID(), 2, FPT_ID, "FPT", "100", "50000", "0", sameInstant),
                buy(UUID.randomUUID(), 3, FPT_ID, "FPT", "100", "60000", "0", sameInstant),
                sell(UUID.randomUUID(), 4, FPT_ID, "FPT", "100", "70000", "0", Instant.parse("2026-08-02T10:00:00Z"))
        );

        PortfolioHoldingsState state = PortfolioAnalyticsV1.replayHoldings(txs, Map.of(FPT_ID, new BigDecimal("75000")));
        PositionResult fpt = state.positions().get(FPT_ID);

        // SequenceNo 2 lot (@50k) was sold first because seq 2 < seq 3
        // Realized PL = 100 * (70k - 50k) = 2,000,000
        assertThat(fpt.realizedPL()).isEqualByComparingTo("2000000");
        // Remaining lot is seq 3 (@60k)
        assertThat(fpt.averageCostBasis()).isEqualByComparingTo("60000");
    }

    @Test
    @DisplayName("Vector 10: Return computed with NetContributedCapital <= 0 is UNAVAILABLE (null)")
    void returnWithZeroOrNegativeCapitalIsUnavailable() {
        List<TransactionInput> txs = List.of(
                deposit(UUID.randomUUID(), 1, "10000000", Instant.parse("2026-08-01T10:00:00Z")),
                withdraw(UUID.randomUUID(), 2, "10000000", Instant.parse("2026-08-02T10:00:00Z"))
        );

        ReturnResult result = PortfolioAnalyticsV1.calculateReturns(
                txs, BigDecimal.ZERO, LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-05"), BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(result.returnSinceInception()).isNull();
        assertThat(result.available()).isFalse();
        assertThat(result.disclosureCode()).isEqualTo("NET_CONTRIBUTED_CAPITAL_METHOD");
    }

    @Test
    @DisplayName("Vector 11: Performance history spanning a data gap carries forward price and flags point PARTIAL")
    void performanceHistoryDataGapForwardFilledAndPartialFlagged() {
        List<TransactionInput> txs = List.of(
                deposit(UUID.randomUUID(), 1, "50000000", Instant.parse("2026-08-01T10:00:00Z")),
                buy(UUID.randomUUID(), 2, FPT_ID, "FPT", "100", "50000", "0", Instant.parse("2026-08-01T10:00:00Z"))
        );

        List<LocalDate> dates = List.of(
                LocalDate.parse("2026-08-01"),
                LocalDate.parse("2026-08-02"), // gap for FPT
                LocalDate.parse("2026-08-03")
        );

        Map<UUID, Map<LocalDate, BigDecimal>> closes = Map.of(
                FPT_ID, Map.of(
                        LocalDate.parse("2026-08-01"), new BigDecimal("50000"),
                        LocalDate.parse("2026-08-03"), new BigDecimal("52000")
                )
        );

        PerformanceHistoryResult history = PortfolioAnalyticsV1.calculatePerformanceHistory(
                txs, dates, closes, LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-03"));

        assertThat(history.series()).hasSize(3);
        // Day 2 should be partial, holding 50,000 from Day 1
        PortfolioAnalyticsV1.DrawdownPoint day2 = history.series().get(1);
        assertThat(day2.partial()).isTrue();
        assertThat(day2.totalValue()).isEqualByComparingTo("50000000"); // 100 * 50k + 45M cash = 50M
    }

    @Test
    @DisplayName("Vector 12: Risk exposure with 2 covered and 1 uncovered position -> weighted score from covered, coverageRatio stated")
    void riskExposureRollupCalculation() {
        List<TransactionInput> txs = List.of(
                deposit(UUID.randomUUID(), 1, "100000000", Instant.parse("2026-08-01T10:00:00Z")),
                buy(UUID.randomUUID(), 2, FPT_ID, "FPT", "100", "50000", "0", Instant.parse("2026-08-01T10:00:00Z")), // Value = 5M
                buy(UUID.randomUUID(), 3, VNM_ID, "VNM", "100", "70000", "0", Instant.parse("2026-08-01T10:00:00Z")), // Value = 7M
                buy(UUID.randomUUID(), 4, HPG_ID, "HPG", "100", "30000", "0", Instant.parse("2026-08-01T10:00:00Z"))  // Value = 3M (uncovered)
        );

        Map<UUID, BigDecimal> prices = Map.of(
                FPT_ID, new BigDecimal("50000"),
                VNM_ID, new BigDecimal("70000"),
                HPG_ID, new BigDecimal("30000")
        );

        PortfolioHoldingsState state = PortfolioAnalyticsV1.replayHoldings(txs, prices);

        // FPT: risk 40, VNM: risk 80 (multiple signals, take max), HPG: no signal
        Map<UUID, List<SignalRiskInput>> signals = Map.of(
                FPT_ID, List.of(new SignalRiskInput(FPT_ID, 40, "MEDIUM")),
                VNM_ID, List.of(new SignalRiskInput(VNM_ID, 60, "MEDIUM"), new SignalRiskInput(VNM_ID, 80, "HIGH")),
                HPG_ID, Collections.emptyList()
        );

        RiskExposureResult risk = PortfolioAnalyticsV1.calculateRiskExposure(state, signals);

        // Covered value = 5M + 7M = 12M. Total position value = 15M.
        assertThat(risk.coveredPositionsValue()).isEqualByComparingTo("12000000");
        assertThat(risk.totalPositionsValue()).isEqualByComparingTo("15000000");
        // Coverage ratio = 12 / 15 = 0.8
        assertThat(risk.coverageRatio()).isEqualByComparingTo("0.800000000000");

        // Weighted score = (5M * 40 + 7M * 80) / 12M = (200M + 560M) / 12M = 760 / 12 = 63.33 -> 63 (MEDIUM)
        assertThat(risk.riskExposureScore()).isEqualTo(63);
        assertThat(risk.riskExposureLevel()).isEqualTo("MEDIUM");
    }

    @Test
    @DisplayName("Vector 13: Reproducibility (U-7) -> identical ledger and inputs produce identical figures across repeated calls")
    void reproducibilityAcrossRepeatedCalls() {
        List<TransactionInput> txs = List.of(
                deposit(UUID.randomUUID(), 1, "50000000", Instant.parse("2026-08-01T10:00:00Z")),
                buy(UUID.randomUUID(), 2, FPT_ID, "FPT", "200", "50000", "10000", Instant.parse("2026-08-02T10:00:00Z")),
                sell(UUID.randomUUID(), 3, FPT_ID, "FPT", "100", "60000", "5000", Instant.parse("2026-08-03T10:00:00Z"))
        );
        Map<UUID, BigDecimal> prices = Map.of(FPT_ID, new BigDecimal("65000"));

        PortfolioHoldingsState run1 = PortfolioAnalyticsV1.replayHoldings(txs, prices);
        PortfolioHoldingsState run2 = PortfolioAnalyticsV1.replayHoldings(txs, prices);

        assertThat(run1.totals().totalValue()).isEqualTo(run2.totals().totalValue());
        assertThat(run1.positions().get(FPT_ID).realizedPL()).isEqualTo(run2.positions().get(FPT_ID).realizedPL());
        assertThat(run1.positions().get(FPT_ID).unrealizedPL()).isEqualTo(run2.positions().get(FPT_ID).unrealizedPL());
    }

    @Test
    @DisplayName("Vector 14 & 15: Analytics period clamping to inception date (F6)")
    void analyticsPeriodClampingToInception() {
        List<TransactionInput> txs = List.of(
                deposit(UUID.randomUUID(), 1, "50000000", Instant.parse("2026-08-10T10:00:00Z"))
        );

        List<LocalDate> dates = List.of(
                LocalDate.parse("2026-08-01"),
                LocalDate.parse("2026-08-10"),
                LocalDate.parse("2026-08-15")
        );

        // Requested from 2026-08-01 (before inception 2026-08-10) -> clamped
        PerformanceHistoryResult historyClamped = PortfolioAnalyticsV1.calculatePerformanceHistory(
                txs, dates, Collections.emptyMap(), LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-15"));

        assertThat(historyClamped.periodClampedToInception()).isTrue();
        assertThat(historyClamped.periodFrom()).isEqualTo(LocalDate.parse("2026-08-10"));

        // Requested from 2026-08-10 (on inception) -> not clamped
        PerformanceHistoryResult historyNotClamped = PortfolioAnalyticsV1.calculatePerformanceHistory(
                txs, dates, Collections.emptyMap(), LocalDate.parse("2026-08-10"), LocalDate.parse("2026-08-15"));

        assertThat(historyNotClamped.periodClampedToInception()).isFalse();
        assertThat(historyNotClamped.periodFrom()).isEqualTo(LocalDate.parse("2026-08-10"));
    }

    @Test
    @DisplayName("Stock & Sector concentration calculations")
    void stockAndSectorConcentration() {
        List<TransactionInput> txs = List.of(
                deposit(UUID.randomUUID(), 1, "100000000", Instant.parse("2026-08-01T10:00:00Z")),
                buy(UUID.randomUUID(), 2, FPT_ID, "FPT", "100", "50000", "0", Instant.parse("2026-08-01T10:00:00Z")), // 5M (Technology)
                buy(UUID.randomUUID(), 3, VNM_ID, "VNM", "100", "50000", "0", Instant.parse("2026-08-01T10:00:00Z"))  // 5M (Consumer)
        );
        // Cash = 90M, Stock = 10M, Total = 100M
        PortfolioHoldingsState state = PortfolioAnalyticsV1.replayHoldings(txs, Map.of(
                FPT_ID, new BigDecimal("50000"),
                VNM_ID, new BigDecimal("50000")
        ));

        List<StockConcentrationItem> stockConc = PortfolioAnalyticsV1.calculateStockConcentration(state);
        assertThat(stockConc).hasSize(2);
        // 5M / 100M = 0.05
        assertThat(stockConc.get(0).weight()).isEqualByComparingTo("0.050000000000");

        Map<UUID, SectorInfo> sectors = Map.of(
                FPT_ID, new SectorInfo("Công nghệ", "Technology"),
                VNM_ID, new SectorInfo("Hàng tiêu dùng", "Consumer Goods")
        );
        List<SectorConcentrationItem> sectorConc = PortfolioAnalyticsV1.calculateSectorConcentration(state, sectors);
        assertThat(sectorConc).hasSize(2);
        assertThat(sectorConc.get(0).weight()).isEqualByComparingTo("0.050000000000");
    }

    @Test
    @DisplayName("Benchmark comparison calculation")
    void benchmarkComparison() {
        // Portfolio return = +10% (0.10), Index from 1200 to 1260 = +5% (0.05) -> Alpha = +5% (0.05)
        BenchmarkComparisonResult result = PortfolioAnalyticsV1.calculateBenchmarkComparison(
                LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-15"),
                new BigDecimal("0.10"), new BigDecimal("1200"), new BigDecimal("1260"));

        assertThat(result.benchmarkReturn()).isEqualByComparingTo("0.050000000000");
        assertThat(result.alpha()).isEqualByComparingTo("0.050000000000");
    }

    @Test
    @DisplayName("Vector 16: Voiding a DEPOSIT that a subsequent WITHDRAW depends on throws INSUFFICIENT_CASH_BALANCE")
    void voidDepositWithDependentWithdrawThrows() {
        UUID depId = UUID.randomUUID();
        List<TransactionInput> existing = List.of(
                deposit(depId, 1, "50000000", Instant.parse("2026-08-01T10:00:00Z")),
                withdraw(UUID.randomUUID(), 2, "30000000", Instant.parse("2026-08-02T10:00:00Z"))
        );

        TransactionInput voidDep = voidTx(UUID.randomUUID(), 3, depId, "Mistake", Instant.parse("2026-08-03T10:00:00Z"));

        assertThatThrownBy(() -> PortfolioAnalyticsV1.validateTransaction(existing, voidDep))
                .isInstanceOf(PortfolioValidationException.class)
                .satisfies(e -> assertThat(((PortfolioValidationException) e).getReasonCode()).isEqualTo(ReasonCode.INSUFFICIENT_CASH_BALANCE));
    }

    @Test
    @DisplayName("Vector 17: Voiding a BUY whose shares were subsequently sold throws LOT_ALREADY_CONSUMED")
    void voidBuyWithSubsequentSellThrows() {
        UUID buyId = UUID.randomUUID();
        List<TransactionInput> existing = List.of(
                deposit(UUID.randomUUID(), 1, "100000000", Instant.parse("2026-08-01T10:00:00Z")),
                buy(buyId, 2, FPT_ID, "FPT", "1000", "50000", "0", Instant.parse("2026-08-02T10:00:00Z")),
                sell(UUID.randomUUID(), 3, FPT_ID, "FPT", "500", "60000", "0", Instant.parse("2026-08-03T10:00:00Z"))
        );

        TransactionInput voidBuy = voidTx(UUID.randomUUID(), 4, buyId, "Mistake", Instant.parse("2026-08-04T10:00:00Z"));

        assertThatThrownBy(() -> PortfolioAnalyticsV1.validateTransaction(existing, voidBuy))
                .isInstanceOf(PortfolioValidationException.class)
                .satisfies(e -> assertThat(((PortfolioValidationException) e).getReasonCode()).isEqualTo(ReasonCode.LOT_ALREADY_CONSUMED));
    }

    // --- Helpers ---

    private static TransactionInput deposit(UUID id, long seq, String amount, Instant executedAt) {
        return new TransactionInput(id, seq, TransactionType.DEPOSIT, null, null, null, null,
                BigDecimal.ZERO, new BigDecimal(amount), "VND", executedAt, executedAt, null, null);
    }

    private static TransactionInput withdraw(UUID id, long seq, String amount, Instant executedAt) {
        return new TransactionInput(id, seq, TransactionType.WITHDRAW, null, null, null, null,
                BigDecimal.ZERO, new BigDecimal(amount), "VND", executedAt, executedAt, null, null);
    }

    private static TransactionInput buy(UUID id, long seq, UUID instId, String symbol, String qty, String price, String fee, Instant executedAt) {
        return new TransactionInput(id, seq, TransactionType.BUY, instId, symbol, new BigDecimal(qty),
                new BigDecimal(price), new BigDecimal(fee), null, "VND", executedAt, executedAt, null, null);
    }

    private static TransactionInput sell(UUID id, long seq, UUID instId, String symbol, String qty, String price, String fee, Instant executedAt) {
        return new TransactionInput(id, seq, TransactionType.SELL, instId, symbol, new BigDecimal(qty),
                new BigDecimal(price), new BigDecimal(fee), null, "VND", executedAt, executedAt, null, null);
    }

    private static TransactionInput voidTx(UUID id, long seq, UUID targetId, String reason, Instant executedAt) {
        return new TransactionInput(id, seq, TransactionType.VOID, null, null, null, null,
                BigDecimal.ZERO, null, "VND", executedAt, executedAt, targetId, reason);
    }
}
